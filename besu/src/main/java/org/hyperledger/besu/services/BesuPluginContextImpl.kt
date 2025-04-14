/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.services

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration
import org.hyperledger.besu.plugin.BesuPlugin
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.BesuService
import org.hyperledger.besu.plugin.services.PluginVersionsProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.StreamSupport

/** The Besu plugin context implementation.  */
class BesuPluginContextImpl
/** Instantiates a new Besu plugin context.  */
    : ServiceManager, PluginVersionsProvider {
    private enum class Lifecycle {
        /** Uninitialized lifecycle.  */
        UNINITIALIZED,

        /** Initialized lifecycle.  */
        INITIALIZED,

        /** Registering lifecycle.  */
        REGISTERING,

        /** Registered lifecycle.  */
        REGISTERED,

        /** Before external services started lifecycle.  */
        BEFORE_EXTERNAL_SERVICES_STARTED,

        /** Before external services finished lifecycle.  */
        BEFORE_EXTERNAL_SERVICES_FINISHED,

        /** Before main loop started lifecycle.  */
        BEFORE_MAIN_LOOP_STARTED,

        /** Before main loop finished lifecycle.  */
        BEFORE_MAIN_LOOP_FINISHED,

        /** Stopping lifecycle.  */
        STOPPING,

        /** Stopped lifecycle.  */
        STOPPED
    }

    private var state = Lifecycle.UNINITIALIZED
    private val serviceRegistry: MutableMap<Class<*>, BesuService> = HashMap()

    private var detectedPlugins: List<BesuPlugin> = ArrayList()
    private var requestedPlugins: List<String> = ArrayList()

    private val registeredPlugins: MutableList<BesuPlugin> = ArrayList()

    private val pluginVersions: MutableList<String> = ArrayList()
    private var config: PluginConfiguration? = null

    /**
     * Add service.
     *
     * @param <T> the type parameter
     * @param serviceType the service type
     * @param service the service
    </T> */
    override fun <T : BesuService> addService(serviceType: Class<T>, service: T) {
        Preconditions.checkArgument(serviceType.isInterface, "Services must be Java interfaces.")
        Preconditions.checkArgument(
            serviceType.isInstance(service),
            "The service registered with a type must implement that type"
        )
        serviceRegistry[serviceType] = service
    }

    override fun <T : BesuService> getService(serviceType: Class<T>): Optional<out BesuService> {
//        return Optional.ofNullable(serviceRegistry[serviceType] as T)
//        return Optional.of(serviceRegistry[serviceType]!! as T)
//        val klass = serviceRegistry[serviceType]!!.javaClass
//        return serviceRegistry[klass] as BesuService?
//        return serviceRegistry[serviceType]
        return Optional.of(serviceRegistry[serviceType]!!)
    }

    private fun detectPlugins(config: PluginConfiguration): List<BesuPlugin> {
        val pluginLoader =
            pluginDirectoryLoader(config.pluginsDir).orElse(javaClass.classLoader)
        val serviceLoader = ServiceLoader.load(BesuPlugin::class.java, pluginLoader)
        return StreamSupport.stream(serviceLoader.spliterator(), false).toList()
    }

    /**
     * Initializes the plugin context with the provided [PluginConfiguration].
     *
     * @param config the plugin configuration
     * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
     */
    fun initialize(config: PluginConfiguration?) {
        Preconditions.checkState(
            state == Lifecycle.UNINITIALIZED,
            "Besu plugins have already been initialized. Cannot register additional plugins."
        )
        this.config = config
        state = Lifecycle.INITIALIZED
    }

    /**
     * Registers plugins based on the provided [PluginConfiguration]. This method finds plugins
     * according to the configuration settings, filters them if necessary and then registers the
     * filtered or found plugins
     *
     * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
     */
    fun registerPlugins() {
        Preconditions.checkState(
            state == Lifecycle.INITIALIZED,
            "Besu plugins have already been registered. Cannot register additional plugins."
        )
        state = Lifecycle.REGISTERING

        if (config!!.isExternalPluginsEnabled) {
            detectedPlugins = detectPlugins(config!!)

            if (config!!.requestedPlugins.isEmpty()) {
                // If no plugins were specified, register all detected plugins
                registerPlugins(detectedPlugins)
            } else {
                // Register only the plugins that were explicitly requested and validated
                requestedPlugins = config!!.requestedPlugins
                // Match and validate the requested plugins against the detected plugins
                val registeringPlugins =
                    matchAndValidateRequestedPlugins(requestedPlugins, detectedPlugins)

                registerPlugins(registeringPlugins)
            }
        } else {
            LOG.debug("External plugins are disabled. Skipping plugins registration.")
        }
        state = Lifecycle.REGISTERED
    }

    @Throws(NoSuchElementException::class)
    private fun matchAndValidateRequestedPlugins(
        requestedPluginNames: List<String>, detectedPlugins: List<BesuPlugin>
    ): List<BesuPlugin> {
        // Filter detected plugins to include only those that match the requested names

        val matchingPlugins =
            detectedPlugins.stream()
                .filter { plugin: BesuPlugin -> requestedPluginNames.contains(plugin.javaClass.simpleName) }
                .toList()

        // Check if all requested plugins were found among the detected plugins
        if (matchingPlugins.size != requestedPluginNames.size) {
            // Find which requested plugins were not matched to throw a detailed exception
            val matchedPluginNames =
                matchingPlugins.stream()
                    .map { plugin: BesuPlugin -> plugin.javaClass.simpleName }
                    .collect(Collectors.toSet())
            val missingPlugins =
                requestedPluginNames.stream()
                    .filter { name: String -> !matchedPluginNames.contains(name) }
                    .collect(Collectors.joining(", "))
            throw NoSuchElementException(
                "The following requested plugins were not found: $missingPlugins"
            )
        }
        return matchingPlugins
    }

    private fun registerPlugins(pluginsToRegister: List<BesuPlugin>) {
        for (plugin in pluginsToRegister) {
            if (registerPlugin(plugin)) {
                registeredPlugins.add(plugin)
            }
        }
    }

    private fun registerPlugin(plugin: BesuPlugin): Boolean {
        try {
            plugin.register(this)
            pluginVersions.add(plugin.version)
            LOG.info("Registered plugin of type {}.", plugin.javaClass.name)
        } catch (e: Exception) {
            if (config!!.isContinueOnPluginError) {
                LOG.error(
                    "Error registering plugin of type {}, start and stop will not be called.",
                    plugin.javaClass.name,
                    e
                )
            } else {
                throw RuntimeException(
                    "Error registering plugin of type " + plugin.javaClass.name, e
                )
            }
            return false
        }
        return true
    }

    /** Before external services.  */
    fun beforeExternalServices() {
        Preconditions.checkState(
            state == Lifecycle.REGISTERED,
            "BesuContext should be in state %s but it was in %s",
            Lifecycle.REGISTERED,
            state
        )
        state = Lifecycle.BEFORE_EXTERNAL_SERVICES_STARTED
        val pluginsIterator = registeredPlugins.iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()

            try {
                plugin.beforeExternalServices()
                LOG.debug(
                    "beforeExternalServices called on plugin of type {}.", plugin.javaClass.name
                )
            } catch (e: Exception) {
                if (config!!.isContinueOnPluginError) {
                    LOG.error(
                        "Error calling `beforeExternalServices` on plugin of type {}, start will not be called.",
                        plugin.javaClass.name,
                        e
                    )
                    pluginsIterator.remove()
                } else {
                    throw RuntimeException(
                        "Error calling `beforeExternalServices` on plugin of type "
                                + plugin.javaClass.name,
                        e
                    )
                }
            }
        }
        LOG.debug("Plugin startup complete.")
        state = Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED
    }

    /** Start plugins.  */
    fun startPlugins() {
        Preconditions.checkState(
            state == Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED,
            "BesuContext should be in state %s but it was in %s",
            Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED,
            state
        )
        state = Lifecycle.BEFORE_MAIN_LOOP_STARTED
        val pluginsIterator = registeredPlugins.iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()

            try {
                plugin.start()
                LOG.debug("Started plugin of type {}.", plugin.javaClass.name)
            } catch (e: Exception) {
                if (config!!.isContinueOnPluginError) {
                    LOG.error(
                        "Error starting plugin of type {}, stop will not be called.",
                        plugin.javaClass.name,
                        e
                    )
                    pluginsIterator.remove()
                } else {
                    throw RuntimeException(
                        "Error starting plugin of type " + plugin.javaClass.name, e
                    )
                }
            }
        }

        LOG.debug("Plugin startup complete.")
        state = Lifecycle.BEFORE_MAIN_LOOP_FINISHED
    }

    /** Execute all plugin setup code after external services.  */
    fun afterExternalServicesMainLoop() {
        Preconditions.checkState(
            state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
            "BesuContext should be in state %s but it was in %s",
            Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
            state
        )
        val pluginsIterator = registeredPlugins.iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()
            try {
                plugin.afterExternalServicePostMainLoop()
            } catch (e: Exception) {
                if (config!!.isContinueOnPluginError) {
                    LOG.error(
                        ("Error calling `afterExternalServicePostMainLoop` on plugin of type "
                                + plugin.javaClass.name
                                + ", stop will not be called."),
                        e
                    )
                    pluginsIterator.remove()
                } else {
                    throw RuntimeException(
                        "Error calling `afterExternalServicePostMainLoop` on plugin of type "
                                + plugin.javaClass.name,
                        e
                    )
                }
            }
        }
    }

    /** Stop plugins.  */
    fun stopPlugins() {
        Preconditions.checkState(
            state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
            "BesuContext should be in state %s but it was in %s",
            Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
            state
        )
        state = Lifecycle.STOPPING

        for (plugin in registeredPlugins) {
            try {
                plugin.stop()
                LOG.debug("Stopped plugin of type {}.", plugin.javaClass.name)
            } catch (e: Exception) {
                LOG.error("Error stopping plugin of type " + plugin.javaClass.name, e)
            }
        }

        LOG.debug("Plugin shutdown complete.")
        state = Lifecycle.STOPPED
    }

    private fun pluginDirectoryLoader(pluginsDir: Path?): Optional<ClassLoader> {
        if (pluginsDir != null && pluginsDir.toFile().isDirectory) {
            LOG.debug("Searching for plugins in {}", pluginsDir.toAbsolutePath())

            try {
                Files.list(pluginsDir).use { pluginFilesList ->
                    val pluginJarURLs =
                        pluginFilesList
                            .filter { p: Path -> p.fileName.toString().endsWith(".jar") }
                            .map<URL?> { p: Path -> pathToURIOrNull(p) }
                            .toArray<URL> { arrayOf() }
                    return Optional.of(URLClassLoader(pluginJarURLs, javaClass.classLoader))
                }
            } catch (e: MalformedURLException) {
                LOG.error("Error converting files to URLs, could not load plugins", e)
            } catch (e: IOException) {
                LOG.error("Error enumerating plugins, could not load plugins", e)
            }
        } else {
            LOG.debug("Plugin directory does not exist, skipping registration. - {}", pluginsDir)
        }

        return Optional.empty()
    }

    override fun getPluginVersions(): Collection<String> {
        return Collections.unmodifiableList(pluginVersions)
    }

    /**
     * Gets plugins.
     *
     * @return the plugins
     */
    @VisibleForTesting
    fun getRegisteredPlugins(): List<BesuPlugin> {
        return Collections.unmodifiableList(registeredPlugins)
    }

    val namedPlugins: Map<String, BesuPlugin?>
        /**
         * Gets named plugins.
         *
         * @return the named plugins
         */
        get() = registeredPlugins.stream()
            .filter { plugin: BesuPlugin -> plugin.name.isPresent }
            .collect(
                Collectors.toMap(
                    Function { plugin: BesuPlugin -> plugin.name.get() },
                    Function { plugin: BesuPlugin? -> plugin },
                    BinaryOperator { a: BesuPlugin?, b: BesuPlugin? -> b })
            )

    val pluginsSummaryLog: List<String>
        /**
         * Generates a summary log of plugin registration. The summary includes registered plugins,
         * detected but not registered (skipped) plugins
         *
         * @return A list of strings, each representing a line in the summary log.
         */
        get() {
            val summary: MutableList<String> = ArrayList()
            summary.add("Plugin Registration Summary:")

            // Log registered plugins with their names and versions
            if (registeredPlugins.isEmpty()) {
                summary.add("No plugins have been registered.")
            } else {
                summary.add("Registered Plugins:")
                registeredPlugins.forEach(
                    Consumer { plugin: BesuPlugin ->
                        summary.add(
                            String.format(
                                " - %s (%s)", plugin.javaClass.simpleName, plugin.version
                            )
                        )
                    })
            }

            // Identify and log detected but not registered (skipped) plugins
            val skippedPlugins =
                detectedPlugins.stream().filter { plugin: BesuPlugin -> !registeredPlugins.contains(plugin) }.toList()

            if (!skippedPlugins.isEmpty()) {
                summary.add("Detected but not registered:")
                skippedPlugins.forEach(
                    Consumer { plugin: BesuPlugin ->
                        summary.add(
                            String.format(
                                " - %s (%s)", plugin.javaClass.simpleName, plugin.version
                            )
                        )
                    })
            }
            summary.add(
                String.format(
                    "TOTAL = %d of %d plugins successfully registered.",
                    registeredPlugins.size, detectedPlugins.size
                )
            )

            return summary
        }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BesuPluginContextImpl::class.java)

        private fun pathToURIOrNull(p: Path): URL? {
            return try {
                p.toUri().toURL()
            } catch (e: MalformedURLException) {
                null
            }
        }
    }
}
