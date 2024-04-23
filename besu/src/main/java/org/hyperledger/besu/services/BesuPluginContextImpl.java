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
package org.hyperledger.besu.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.PluginVersionsProvider;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Besu plugin context implementation. */
public class BesuPluginContextImpl implements BesuContext, PluginVersionsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(BesuPluginContextImpl.class);

  private enum Lifecycle {
    /** Uninitialized lifecycle. */
    UNINITIALIZED,
    /** Registering lifecycle. */
    REGISTERING,
    /** Registered lifecycle. */
    REGISTERED,
    /** Before external services started lifecycle. */
    BEFORE_EXTERNAL_SERVICES_STARTED,
    /** Before external services finished lifecycle. */
    BEFORE_EXTERNAL_SERVICES_FINISHED,
    /** Before main loop started lifecycle. */
    BEFORE_MAIN_LOOP_STARTED,
    /** Before main loop finished lifecycle. */
    BEFORE_MAIN_LOOP_FINISHED,
    /** Stopping lifecycle. */
    STOPPING,
    /** Stopped lifecycle. */
    STOPPED
  }

  private Lifecycle state = Lifecycle.UNINITIALIZED;
  private final Map<Class<?>, ? super BesuService> serviceRegistry = new HashMap<>();

  private List<BesuPlugin> detectedPlugins = new ArrayList<>();
  private List<String> requestedPlugins = new ArrayList<>();

  private final List<BesuPlugin> registeredPlugins = new ArrayList<>();

  private final List<String> pluginVersions = new ArrayList<>();

  /**
   * Add service.
   *
   * @param <T> the type parameter
   * @param serviceType the service type
   * @param service the service
   */
  @Override
  public <T extends BesuService> void addService(final Class<T> serviceType, final T service) {
    checkArgument(serviceType.isInterface(), "Services must be Java interfaces.");
    checkArgument(
        serviceType.isInstance(service),
        "The service registered with a type must implement that type");
    serviceRegistry.put(serviceType, service);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BesuService> Optional<T> getService(final Class<T> serviceType) {
    return Optional.ofNullable((T) serviceRegistry.get(serviceType));
  }

  private List<BesuPlugin> detectPlugins(final PluginConfiguration config) {
    ClassLoader pluginLoader =
        pluginDirectoryLoader(config.getPluginsDir()).orElse(getClass().getClassLoader());
    ServiceLoader<BesuPlugin> serviceLoader = ServiceLoader.load(BesuPlugin.class, pluginLoader);
    return StreamSupport.stream(serviceLoader.spliterator(), false).toList();
  }

  /**
   * Registers plugins based on the provided {@link PluginConfiguration}. This method finds plugins
   * according to the configuration settings, filters them if necessary and then registers the
   * filtered or found plugins
   *
   * @param config The configuration settings used to find and filter plugins for registration. The
   *     configuration includes the plugin directory and any configured plugin identifiers if
   *     applicable.
   * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
   */
  public void registerPlugins(final PluginConfiguration config) {
    checkState(
        state == Lifecycle.UNINITIALIZED,
        "Besu plugins have already been registered. Cannot register additional plugins.");
    state = Lifecycle.REGISTERING;

    detectedPlugins = detectPlugins(config);
    if (!config.getRequestedPlugins().isEmpty()) {
      // Register only the plugins that were explicitly requested and validated
      requestedPlugins = config.getRequestedPlugins();

      // Match and validate the requested plugins against the detected plugins
      List<BesuPlugin> registeringPlugins =
          matchAndValidateRequestedPlugins(requestedPlugins, detectedPlugins);

      registerPlugins(registeringPlugins);
    } else {
      // If no plugins were specified, register all detected plugins
      registerPlugins(detectedPlugins);
    }
  }

  private List<BesuPlugin> matchAndValidateRequestedPlugins(
      final List<String> requestedPluginNames, final List<BesuPlugin> detectedPlugins)
      throws NoSuchElementException {

    // Filter detected plugins to include only those that match the requested names
    List<BesuPlugin> matchingPlugins =
        detectedPlugins.stream()
            .filter(plugin -> requestedPluginNames.contains(plugin.getClass().getSimpleName()))
            .toList();

    // Check if all requested plugins were found among the detected plugins
    if (matchingPlugins.size() != requestedPluginNames.size()) {
      // Find which requested plugins were not matched to throw a detailed exception
      Set<String> matchedPluginNames =
          matchingPlugins.stream()
              .map(plugin -> plugin.getClass().getSimpleName())
              .collect(Collectors.toSet());
      String missingPlugins =
          requestedPluginNames.stream()
              .filter(name -> !matchedPluginNames.contains(name))
              .collect(Collectors.joining(", "));
      throw new NoSuchElementException(
          "The following requested plugins were not found: " + missingPlugins);
    }
    return matchingPlugins;
  }

  private void registerPlugins(final List<BesuPlugin> pluginsToRegister) {

    for (final BesuPlugin plugin : pluginsToRegister) {
      if (registerPlugin(plugin)) {
        registeredPlugins.add(plugin);
      }
    }
    state = Lifecycle.REGISTERED;
  }

  private boolean registerPlugin(final BesuPlugin plugin) {
    try {
      plugin.register(this);
      LOG.info("Registered plugin of type {}.", plugin.getClass().getName());
      pluginVersions.add(plugin.getVersion());
    } catch (final Exception e) {
      LOG.error(
          "Error registering plugin of type "
              + plugin.getClass().getName()
              + ", start and stop will not be called.",
          e);
      return false;
    }
    return true;
  }

  /** Before external services. */
  public void beforeExternalServices() {
    checkState(
        state == Lifecycle.REGISTERED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.REGISTERED,
        state);
    state = Lifecycle.BEFORE_EXTERNAL_SERVICES_STARTED;
    final Iterator<BesuPlugin> pluginsIterator = registeredPlugins.iterator();

    while (pluginsIterator.hasNext()) {
      final BesuPlugin plugin = pluginsIterator.next();

      try {
        plugin.beforeExternalServices();
        LOG.debug(
            "beforeExternalServices called on plugin of type {}.", plugin.getClass().getName());
      } catch (final Exception e) {
        LOG.error(
            "Error calling `beforeExternalServices` on plugin of type "
                + plugin.getClass().getName()
                + ", stop will not be called.",
            e);
        pluginsIterator.remove();
      }
    }

    LOG.debug("Plugin startup complete.");
    state = Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED;
  }

  /** Start plugins. */
  public void startPlugins() {
    checkState(
        state == Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.BEFORE_EXTERNAL_SERVICES_FINISHED,
        state);
    state = Lifecycle.BEFORE_MAIN_LOOP_STARTED;
    final Iterator<BesuPlugin> pluginsIterator = registeredPlugins.iterator();

    while (pluginsIterator.hasNext()) {
      final BesuPlugin plugin = pluginsIterator.next();

      try {
        plugin.start();
        LOG.debug("Started plugin of type {}.", plugin.getClass().getName());
      } catch (final Exception e) {
        LOG.error(
            "Error starting plugin of type "
                + plugin.getClass().getName()
                + ", stop will not be called.",
            e);
        pluginsIterator.remove();
      }
    }

    LOG.debug("Plugin startup complete.");
    state = Lifecycle.BEFORE_MAIN_LOOP_FINISHED;
  }

  /** Stop plugins. */
  public void stopPlugins() {
    checkState(
        state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        state);
    state = Lifecycle.STOPPING;

    for (final BesuPlugin plugin : registeredPlugins) {
      try {
        plugin.stop();
        LOG.debug("Stopped plugin of type {}.", plugin.getClass().getName());
      } catch (final Exception e) {
        LOG.error("Error stopping plugin of type " + plugin.getClass().getName(), e);
      }
    }

    LOG.debug("Plugin shutdown complete.");
    state = Lifecycle.STOPPED;
  }

  private static URL pathToURIOrNull(final Path p) {
    try {
      return p.toUri().toURL();
    } catch (final MalformedURLException e) {
      return null;
    }
  }

  private Optional<ClassLoader> pluginDirectoryLoader(final Path pluginsDir) {
    if (pluginsDir != null && pluginsDir.toFile().isDirectory()) {
      LOG.debug("Searching for plugins in {}", pluginsDir.toAbsolutePath());

      try (final Stream<Path> pluginFilesList = Files.list(pluginsDir)) {
        final URL[] pluginJarURLs =
            pluginFilesList
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .map(BesuPluginContextImpl::pathToURIOrNull)
                .toArray(URL[]::new);
        return Optional.of(new URLClassLoader(pluginJarURLs, this.getClass().getClassLoader()));
      } catch (final MalformedURLException e) {
        LOG.error("Error converting files to URLs, could not load plugins", e);
      } catch (final IOException e) {
        LOG.error("Error enumerating plugins, could not load plugins", e);
      }
    } else {
      LOG.debug("Plugin directory does not exist, skipping registration. - {}", pluginsDir);
    }

    return Optional.empty();
  }

  @Override
  public Collection<String> getPluginVersions() {
    return Collections.unmodifiableList(pluginVersions);
  }

  /**
   * Gets plugins.
   *
   * @return the plugins
   */
  @VisibleForTesting
  List<BesuPlugin> getRegisteredPlugins() {
    return Collections.unmodifiableList(registeredPlugins);
  }

  /**
   * Gets named plugins.
   *
   * @return the named plugins
   */
  public Map<String, BesuPlugin> getNamedPlugins() {
    return registeredPlugins.stream()
        .filter(plugin -> plugin.getName().isPresent())
        .collect(Collectors.toMap(plugin -> plugin.getName().get(), plugin -> plugin, (a, b) -> b));
  }

  /**
   * Generates a summary log of plugin registration. The summary includes registered plugins,
   * detected but not registered (skipped) plugins
   *
   * @return A list of strings, each representing a line in the summary log.
   */
  public List<String> getPluginsSummaryLog() {
    List<String> summary = new ArrayList<>();
    summary.add("Plugin Registration Summary:");

    // Log registered plugins with their names and versions
    if (registeredPlugins.isEmpty()) {
      summary.add("No plugins have been registered.");
    } else {
      summary.add("Registered Plugins:");
      registeredPlugins.forEach(
          plugin ->
              summary.add(
                  String.format(
                      " - %s (Version: %s)",
                      plugin.getClass().getSimpleName(), plugin.getVersion())));
    }

    // Identify and log detected but not registered (skipped) plugins
    List<String> skippedPlugins =
        detectedPlugins.stream()
            .filter(plugin -> !registeredPlugins.contains(plugin))
            .map(plugin -> plugin.getClass().getSimpleName())
            .toList();

    if (!skippedPlugins.isEmpty()) {
      summary.add("Skipped Plugins:");
      skippedPlugins.forEach(
          pluginName ->
              summary.add(String.format(" - %s (Detected but not registered)", pluginName)));
    }
    summary.add(
        String.format(
            "TOTAL = %d of %d plugins successfully registered.",
            registeredPlugins.size(), detectedPlugins.size()));

    return summary;
  }
}
