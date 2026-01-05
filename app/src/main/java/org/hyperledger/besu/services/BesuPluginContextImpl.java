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
import org.hyperledger.besu.ethereum.core.plugins.PluginsVerificationMode;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.PluginVersionsProvider;
import org.hyperledger.besu.util.BesuVersionUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/** The Besu plugin context implementation. */
public class BesuPluginContextImpl implements ServiceManager, PluginVersionsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(BesuPluginContextImpl.class);

  private enum Lifecycle {
    /** Uninitialized lifecycle. */
    UNINITIALIZED,
    /** Initialized lifecycle. */
    INITIALIZED,
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

  private final Map<String, String> pluginVersions = new LinkedHashMap<>();
  private PluginConfiguration config;

  /** Instantiates a new Besu plugin context. */
  public BesuPluginContextImpl() {}

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

  /**
   * Initializes the plugin context with the provided {@link PluginConfiguration}.
   *
   * @param config the plugin configuration
   * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
   */
  public void initialize(final PluginConfiguration config) {
    checkState(
        state == Lifecycle.UNINITIALIZED,
        "Besu plugins have already been initialized. Cannot register additional plugins.");
    this.config = config;
    state = Lifecycle.INITIALIZED;
  }

  /**
   * Registers plugins based on the provided {@link PluginConfiguration}. This method finds plugins
   * according to the configuration settings, filters them if necessary and then registers the
   * filtered or found plugins
   *
   * @throws IllegalStateException if the system is not in the UNINITIALIZED state.
   */
  public void registerPlugins() {
    checkState(
        state == Lifecycle.INITIALIZED,
        "Besu plugins have already been registered. Cannot register additional plugins.");
    state = Lifecycle.REGISTERING;

    if (config.isExternalPluginsEnabled()) {
      detectedPlugins = detectPlugins(config);

      if (config.getRequestedPlugins().isEmpty()) {
        // If no plugins were specified, register all detected plugins
        registerPlugins(detectedPlugins);
      } else {
        // Register only the plugins that were explicitly requested and validated
        requestedPlugins = config.getRequestedPlugins();
        // Match and validate the requested plugins against the detected plugins
        List<BesuPlugin> registeringPlugins =
            matchAndValidateRequestedPlugins(requestedPlugins, detectedPlugins);

        registerPlugins(registeringPlugins);
      }
    } else {
      LOG.debug("External plugins are disabled. Skipping plugins registration.");
    }
    state = Lifecycle.REGISTERED;
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
  }

  private boolean registerPlugin(final BesuPlugin plugin) {
    try {
      plugin.register(this);
      pluginVersions.put(plugin.getName().orElse("<Unnamed Plugin>"), plugin.getVersion());
      LOG.info("Registered plugin of type {}.", plugin.getClass().getName());
    } catch (final Exception e) {
      if (config.isContinueOnPluginError()) {
        LOG.error(
            "Error registering plugin of type {}, start and stop will not be called.",
            plugin.getClass().getName(),
            e);
      } else {
        throw new RuntimeException(
            "Error registering plugin of type " + plugin.getClass().getName(), e);
      }
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
        if (config.isContinueOnPluginError()) {
          LOG.error(
              "Error calling `beforeExternalServices` on plugin of type {}, start will not be called.",
              plugin.getClass().getName(),
              e);
          pluginsIterator.remove();
        } else {
          throw new RuntimeException(
              "Error calling `beforeExternalServices` on plugin of type "
                  + plugin.getClass().getName(),
              e);
        }
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
        if (config.isContinueOnPluginError()) {
          LOG.error(
              "Error starting plugin of type {}, stop will not be called.",
              plugin.getClass().getName(),
              e);
          pluginsIterator.remove();
        } else {
          throw new RuntimeException(
              "Error starting plugin of type " + plugin.getClass().getName(), e);
        }
      }
    }

    LOG.debug("Plugin startup complete.");
    state = Lifecycle.BEFORE_MAIN_LOOP_FINISHED;
  }

  /** Execute all plugin setup code after external services. */
  public void afterExternalServicesMainLoop() {
    checkState(
        state == Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        "BesuContext should be in state %s but it was in %s",
        Lifecycle.BEFORE_MAIN_LOOP_FINISHED,
        state);
    final Iterator<BesuPlugin> pluginsIterator = registeredPlugins.iterator();

    while (pluginsIterator.hasNext()) {
      final BesuPlugin plugin = pluginsIterator.next();
      try {
        plugin.afterExternalServicePostMainLoop();
      } catch (final Exception e) {
        if (config.isContinueOnPluginError()) {
          LOG.error(
              "Error calling `afterExternalServicePostMainLoop` on plugin of type "
                  + plugin.getClass().getName()
                  + ", stop will not be called.",
              e);
          pluginsIterator.remove();
        } else {
          throw new RuntimeException(
              "Error calling `afterExternalServicePostMainLoop` on plugin of type "
                  + plugin.getClass().getName(),
              e);
        }
      }
    }
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

  private List<BesuPlugin> detectPlugins(final PluginConfiguration config) {
    final var pluginsDir = config.getPluginsDir();
    if (pluginsDir != null && pluginsDir.toFile().isDirectory()) {
      LOG.debug("Searching for plugins in {}", pluginsDir.toAbsolutePath());

      try (final Stream<Path> pluginFilesList = Files.list(pluginsDir)) {
        final URL[] pluginJarURLs =
            pluginFilesList
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .map(BesuPluginContextImpl::pathToURIOrNull)
                .toArray(URL[]::new);
        final var pluginClassLoader = new URLClassLoader(pluginJarURLs);
        ServiceLoader<BesuPlugin> serviceLoader =
            ServiceLoader.load(BesuPlugin.class, pluginClassLoader);
        final var foundPlugins = StreamSupport.stream(serviceLoader.spliterator(), false).toList();
        verifyPlugins(config.getPluginsVerificationMode(), pluginClassLoader, foundPlugins);

        return foundPlugins;
      } catch (final MalformedURLException e) {
        LOG.error("Error converting files to URLs, could not load plugins", e);
      } catch (final IOException e) {
        LOG.error("Error enumerating plugins, could not load plugins", e);
      }
    } else {
      LOG.debug("Plugin directory does not exist, skipping registration. - {}", pluginsDir);
    }

    return List.of();
  }

  private void verifyPlugins(
      final PluginsVerificationMode pluginsVerificationMode,
      final URLClassLoader pluginClassLoader,
      final List<BesuPlugin> plugins)
      throws IOException {

    final var pluginsArtifactData = getPluginsArtifactData(pluginClassLoader, plugins);
    LOG.debug("Loaded pluginsArtifactData: {}", pluginsArtifactData);

    final var catalogLessArtifacts =
        pluginsArtifactData.stream()
            .filter(ad -> ad.catalog().equals(ArtifactCatalog.NO_CATALOG))
            .toList();

    if (!catalogLessArtifacts.isEmpty()) {
      final LoggingEventBuilder leb =
          pluginsVerificationMode.failOnCatalogLess() ? LOG.atError() : LOG.atWarn();
      catalogLessArtifacts.forEach(
          ad ->
              leb.log(
                  "Artifact {} containing plugins {} is without a catalog",
                  ad.name(),
                  ad.plugins().stream().map(BesuPlugin::getName).map(Optional::get).toList()));

      if (pluginsVerificationMode.failOnCatalogLess()) {
        throw new IllegalStateException(
            "Plugins verification failed, see previous logs for more details.");
      }
    }

    verifyCatalogs(pluginClassLoader, pluginsVerificationMode, pluginsArtifactData);
  }

  private List<ArtifactInfo> getPluginsArtifactData(
      final URLClassLoader pluginClassLoader, final List<BesuPlugin> plugins) throws IOException {
    final var pluginsByArtifact =
        plugins.stream()
            .collect(
                Collectors.groupingBy(
                    plugin -> {
                      try {
                        return plugin
                            .getClass()
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
                            .getSchemeSpecificPart();
                      } catch (URISyntaxException e) {
                        throw new RuntimeException(
                            "Error getting artifact URL for plugin " + plugin.getName(), e);
                      }
                    }));

    final var allCatalogs =
        Collections.list(pluginClassLoader.getResources("META-INF/plugin-artifacts-catalog.json"));

    final var artifactsWithCatalogs =
        allCatalogs.stream()
            .collect(
                Collectors.toMap(
                    url -> {
                      try {
                        return StringUtils.substringBefore(new URI(url.getPath()).getPath(), '!');
                      } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                      }
                    },
                    ArtifactCatalog::load));

    return pluginsByArtifact.entrySet().stream()
        .map(
            entry ->
                new ArtifactInfo(
                    entry.getKey(),
                    entry.getValue(),
                    artifactsWithCatalogs.getOrDefault(entry.getKey(), ArtifactCatalog.NO_CATALOG)))
        .toList();
  }

  private void verifyCatalogs(
      final URLClassLoader pluginClassLoader,
      final PluginsVerificationMode pluginsVerificationMode,
      final List<ArtifactInfo> pluginsArtifactData)
      throws IOException {
    verifyBesuVersions(pluginsVerificationMode, pluginsArtifactData);
    verifyBesuPluginsDependencyConflicts(
        pluginClassLoader, pluginsVerificationMode, pluginsArtifactData);
    verifyPluginsDependencyConflicts(pluginsVerificationMode, pluginsArtifactData);
  }

  private void verifyBesuVersions(
      final PluginsVerificationMode pluginsVerificationMode,
      final List<ArtifactInfo> pluginsArtifactData) {
    final var besuRunningVersion = BesuVersionUtils.shortVersion();
    final var besuVersionConflictFound =
        pluginsArtifactData.stream()
            .anyMatch(
                artifactInfo -> {
                  final var pluginBuildBesuVersion = artifactInfo.catalog().besuVersion();
                  if (!besuRunningVersion.equals(pluginBuildBesuVersion)) {
                    final LoggingEventBuilder leb =
                        pluginsVerificationMode.failOnBesuVersionConflict()
                            ? LOG.atError()
                            : LOG.atWarn();
                    leb.log(
                        "Plugin artifact {} is built against Besu version {} while current running Besu version is {}",
                        artifactInfo.name(),
                        pluginBuildBesuVersion,
                        besuRunningVersion);
                    return true;
                  }
                  return false;
                });

    if (besuVersionConflictFound && pluginsVerificationMode.failOnBesuVersionConflict()) {
      throw new IllegalStateException(
          "Plugins verification failed, see previous logs for more details.");
    }
  }

  private void verifyPluginsDependencyConflicts(
      final PluginsVerificationMode pluginsVerificationMode,
      final List<ArtifactInfo> pluginsArtifactInfo) {
    final var alreadyChecked = HashSet.<ArtifactInfo>newHashSet(pluginsArtifactInfo.size());

    boolean verificationFailed = false;

    for (final var artifactInfo : pluginsArtifactInfo) {
      alreadyChecked.add(artifactInfo);
      for (final var dep : artifactInfo.catalog().dependencies()) {
        for (final var otherArtifactInfo : pluginsArtifactInfo) {
          if (!alreadyChecked.contains(otherArtifactInfo)) {
            final var otherCatalog = otherArtifactInfo.catalog();
            final var maybeOtherDep = otherCatalog.getByNameAndGroup(dep);
            if (maybeOtherDep.isPresent()) {
              final var otherDep = maybeOtherDep.get();
              if (!dep.version().equals(otherDep.version())) {
                verificationFailed = pluginsVerificationMode.failOnPluginDependencyConflict();
                final LoggingEventBuilder leb = verificationFailed ? LOG.atError() : LOG.atWarn();
                leb.log(
                    "Plugin artifacts {} and {} bring the same dependency {}:{} but with different versions: {} and {}",
                    artifactInfo.name(),
                    otherArtifactInfo.name(),
                    dep.group(),
                    dep.name(),
                    dep.version(),
                    otherDep.version());
              }
            }
          }
        }
      }
    }

    if (verificationFailed) {
      throw new IllegalStateException(
          "Plugins verification failed, see previous logs for more details.");
    }
  }

  private void verifyBesuPluginsDependencyConflicts(
      final URLClassLoader pluginClassLoader,
      final PluginsVerificationMode pluginsVerificationMode,
      final List<ArtifactInfo> pluginsArtifactData)
      throws IOException {
    final var besuArtifactsCatalog =
        Collections.list(pluginClassLoader.getResources("META-INF/besu-artifacts-catalog.json"));

    final var besuDependencies = ArtifactDependency.loadList(besuArtifactsCatalog.get(0));

    besuDependencies.forEach(
        besuDependency -> {
          final var conflicts =
              pluginsArtifactData.stream()
                  .filter(pad -> pad.catalog().containsByNameAndGroup(besuDependency))
                  .toList();
          if (!conflicts.isEmpty()) {
            final LoggingEventBuilder leb =
                pluginsVerificationMode.failOnBesuDependencyConflict()
                    ? LOG.atError()
                    : LOG.atWarn();
            conflicts.forEach(
                ai -> {
                  leb.log(
                      "Plugin artifact {} brings the dependency {} that is already provided by Besu",
                      ai.name(),
                      besuDependency.group() + ":" + besuDependency.name());
                });
            if (pluginsVerificationMode.failOnBesuDependencyConflict()) {
              throw new IllegalStateException(
                  "Plugins verification failed, see previous logs for more details.");
            }
          }
        });
  }

  @Override
  public Map<String, String> getPluginVersions() {
    return Collections.unmodifiableMap(pluginVersions);
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
                      " - %s (%s)", plugin.getClass().getSimpleName(), plugin.getVersion())));
    }

    // Identify and log detected but not registered (skipped) plugins
    List<BesuPlugin> skippedPlugins =
        detectedPlugins.stream().filter(plugin -> !registeredPlugins.contains(plugin)).toList();

    if (!skippedPlugins.isEmpty()) {
      summary.add("Detected but not registered:");
      skippedPlugins.forEach(
          plugin ->
              summary.add(
                  String.format(
                      " - %s (%s)", plugin.getClass().getSimpleName(), plugin.getVersion())));
    }
    summary.add(
        String.format(
            "TOTAL = %d of %d plugins successfully registered.",
            registeredPlugins.size(), detectedPlugins.size()));

    return summary;
  }

  record ArtifactInfo(String name, List<BesuPlugin> plugins, ArtifactCatalog catalog) {
    @Override
    public String toString() {
      return "ArtifactInfo{"
          + "name='"
          + name
          + '\''
          + ", plugins="
          + plugins.stream()
              .map(BesuPlugin::getName)
              .map(o -> o.orElse("_unnamed_"))
              .collect(Collectors.joining(", "))
          + ", catalog="
          + catalog
          + '}';
    }
  }

  record ArtifactCatalog(String besuVersion, List<ArtifactDependency> dependencies) {
    static final ArtifactCatalog NO_CATALOG = new ArtifactCatalog(null, List.of());

    static ArtifactCatalog load(final URL url) {
      final var objectMapper = new ObjectMapper();
      try {
        return objectMapper.readValue(url, ArtifactCatalog.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public boolean containsByNameAndGroup(final ArtifactDependency other) {
      return dependencies.stream().anyMatch(other::equalsByNameAndGroup);
    }

    public Optional<ArtifactDependency> getByNameAndGroup(final ArtifactDependency other) {
      return dependencies.stream().filter(other::equalsByNameAndGroup).findFirst();
    }
  }

  record ArtifactDependency(
      String name, String group, String version, String classifier, String filename) {
    boolean equalsByNameAndGroup(final ArtifactDependency other) {
      return Objects.equals(name, other.name) && Objects.equals(group, other.group);
    }

    static List<ArtifactDependency> loadList(final URL url) {
      final var objectMapper = new ObjectMapper();
      try {
        return objectMapper.readValue(url, new TypeReference<>() {});
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
