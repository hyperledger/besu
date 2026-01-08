/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.ethereum.core.plugins.PluginsVerificationMode;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.util.BesuVersionUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

public class PluginVerifier {
  private static final Logger LOG = LoggerFactory.getLogger(PluginVerifier.class);

  private static final String PLUGIN_CATALOG_PATH = "META-INF/plugin-artifacts-catalog.json";
  private static final String BESU_CATALOG_PATH = "META-INF/besu-artifacts-catalog.json";

  static void verify(
      final PluginsVerificationMode pluginsVerificationMode,
      final URLClassLoader pluginClassLoader,
      final List<BesuPlugin> plugins)
      throws IOException {

    verifyNamesAreUnique(plugins);

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
                  ad.plugins().stream().map(BesuPlugin::getName).toList()));

      if (pluginsVerificationMode.failOnCatalogLess()) {
        throw new PluginVerificationException(
            "Plugins verification failed, see previous logs for more details.");
      }
    }

    verifyCatalogs(pluginClassLoader, pluginsVerificationMode, pluginsArtifactData);
  }

  private static void verifyNamesAreUnique(final List<BesuPlugin> loadedPlugins) {
    final var pluginNameCounts =
        loadedPlugins.stream()
            .collect(Collectors.groupingBy(BesuPlugin::getName, Collectors.counting()));

    final var duplicateNames =
        pluginNameCounts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();

    if (!duplicateNames.isEmpty()) {
      throw new PluginVerificationException(
          "Plugins with same name detected: " + String.join(", ", duplicateNames));
    }
  }

  private static List<ArtifactInfo> getPluginsArtifactData(
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
                        throw new PluginVerificationException(
                            "Error getting artifact URL for plugin " + plugin.getName(), e);
                      }
                    }));

    final var allCatalogs = Collections.list(pluginClassLoader.getResources(PLUGIN_CATALOG_PATH));

    final var artifactsWithCatalogs =
        allCatalogs.stream()
            .collect(
                Collectors.toMap(
                    url -> {
                      try {
                        // JAR URLs have format jar:file:/path/to/file.jar!/entry.
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

  private static void verifyCatalogs(
      final URLClassLoader pluginClassLoader,
      final PluginsVerificationMode pluginsVerificationMode,
      final List<ArtifactInfo> pluginsArtifactData)
      throws IOException {
    verifyBesuVersions(pluginsVerificationMode, pluginsArtifactData);
    verifyBesuPluginsDependencyConflicts(
        pluginClassLoader, pluginsVerificationMode, pluginsArtifactData);
    verifyPluginsDependencyConflicts(pluginsVerificationMode, pluginsArtifactData);
  }

  private static void verifyBesuVersions(
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
                        pluginBuildBesuVersion == null ? "unknown" : pluginBuildBesuVersion,
                        besuRunningVersion);
                    return true;
                  }
                  return false;
                });

    if (besuVersionConflictFound && pluginsVerificationMode.failOnBesuVersionConflict()) {
      throw new PluginVerificationException(
          "Plugins verification failed, see previous logs for more details.");
    }
  }

  private static void verifyPluginsDependencyConflicts(
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
                final boolean shouldFail = pluginsVerificationMode.failOnPluginDependencyConflict();
                verificationFailed = verificationFailed || shouldFail;
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
      throw new PluginVerificationException(
          "Plugins verification failed, see previous logs for more details.");
    }
  }

  private static void verifyBesuPluginsDependencyConflicts(
      final URLClassLoader pluginClassLoader,
      final PluginsVerificationMode pluginsVerificationMode,
      final List<ArtifactInfo> pluginsArtifactData)
      throws IOException {
    final var besuArtifactsCatalog =
        Collections.list(pluginClassLoader.getResources(BESU_CATALOG_PATH));

    if (besuArtifactsCatalog.isEmpty()) {
      throw new PluginVerificationException("No Besu artifacts catalog found");
    }

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
                      "Plugin artifact {} brings the dependency {}:{} that is already provided by Besu",
                      ai.name(),
                      besuDependency.group(),
                      besuDependency.name());
                });
            if (pluginsVerificationMode.failOnBesuDependencyConflict()) {
              throw new PluginVerificationException(
                  "Plugin verification failed due to dependency conflict, see logs for more details.");
            }
          }
        });
  }

  private record ArtifactInfo(String name, List<BesuPlugin> plugins, ArtifactCatalog catalog) {
    @Override
    public String toString() {
      return "ArtifactInfo{"
          + "name='"
          + name
          + '\''
          + ", plugins="
          + plugins.stream().map(BesuPlugin::getName).collect(Collectors.joining(", "))
          + ", catalog="
          + catalog
          + '}';
    }
  }

  private record ArtifactCatalog(String besuVersion, List<ArtifactDependency> dependencies) {
    static final ArtifactCatalog NO_CATALOG = new ArtifactCatalog(null, List.of());

    static ArtifactCatalog load(final URL url) {
      final var objectMapper = new ObjectMapper();
      try {
        return objectMapper.readValue(url, ArtifactCatalog.class);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    boolean containsByNameAndGroup(final ArtifactDependency other) {
      return dependencies.stream().anyMatch(other::equalsByNameAndGroup);
    }

    Optional<ArtifactDependency> getByNameAndGroup(final ArtifactDependency other) {
      return dependencies.stream().filter(other::equalsByNameAndGroup).findFirst();
    }
  }

  private record ArtifactDependency(
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

  private static class PluginVerificationException extends RuntimeException {
    PluginVerificationException(final String message) {
      super(message);
    }

    PluginVerificationException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
