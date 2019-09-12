/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import tech.pegasys.pantheon.plugin.PantheonContext;
import tech.pegasys.pantheon.plugin.PantheonPlugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PantheonPluginContextImpl implements PantheonContext {

  private static final Logger LOG = LogManager.getLogger();

  private enum Lifecycle {
    UNINITIALIZED,
    REGISTERING,
    REGISTERED,
    STARTING,
    STARTED,
    STOPPING,
    STOPPED
  }

  private Lifecycle state = Lifecycle.UNINITIALIZED;
  private final Map<Class<?>, ? super Object> serviceRegistry = new HashMap<>();
  private final List<PantheonPlugin> plugins = new ArrayList<>();

  public <T> void addService(final Class<T> serviceType, final T service) {
    checkArgument(serviceType.isInterface(), "Services must be Java interfaces.");
    checkArgument(
        serviceType.isInstance(service),
        "The service registered with a type must implement that type");
    serviceRegistry.put(serviceType, service);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Optional<T> getService(final Class<T> serviceType) {
    return Optional.ofNullable((T) serviceRegistry.get(serviceType));
  }

  public void registerPlugins(final Path pluginsDir) {
    checkState(
        state == Lifecycle.UNINITIALIZED,
        "Pantheon plugins have already been registered.  Cannot register additional plugins.");

    final ClassLoader pluginLoader =
        pluginDirectoryLoader(pluginsDir).orElse(this.getClass().getClassLoader());

    state = Lifecycle.REGISTERING;

    final ServiceLoader<PantheonPlugin> serviceLoader =
        ServiceLoader.load(PantheonPlugin.class, pluginLoader);

    for (final PantheonPlugin plugin : serviceLoader) {
      try {
        plugin.register(this);
        LOG.debug("Registered plugin of type {}.", plugin.getClass().getName());
      } catch (final Exception e) {
        LOG.error(
            "Error registering plugin of type {}, start and stop will not be called. \n{}",
            plugin.getClass(),
            e);
        continue;
      }
      plugins.add(plugin);
    }

    LOG.debug("Plugin registration complete.");

    state = Lifecycle.REGISTERED;
  }

  public void startPlugins() {
    checkState(
        state == Lifecycle.REGISTERED,
        "PantheonContext should be in state %s but it was in %s",
        Lifecycle.REGISTERED,
        state);
    state = Lifecycle.STARTING;
    final Iterator<PantheonPlugin> pluginsIterator = plugins.iterator();

    while (pluginsIterator.hasNext()) {
      final PantheonPlugin plugin = pluginsIterator.next();

      try {
        plugin.start();
        LOG.debug("Started plugin of type {}.", plugin.getClass().getName());
      } catch (final Exception e) {
        LOG.error(
            "Error starting plugin of type {}, stop will not be called. \n{}",
            plugin.getClass(),
            e);
        pluginsIterator.remove();
      }
    }

    LOG.debug("Plugin startup complete.");
    state = Lifecycle.STARTED;
  }

  public void stopPlugins() {
    checkState(
        state == Lifecycle.STARTED,
        "PantheonContext should be in state %s but it was in %s",
        Lifecycle.STARTED,
        state);
    state = Lifecycle.STOPPING;

    for (final PantheonPlugin plugin : plugins) {
      try {
        plugin.stop();
        LOG.debug("Stopped plugin of type {}.", plugin.getClass().getName());
      } catch (final Exception e) {
        LOG.error("Error stopping plugin of type {}. \n{}", plugin.getClass(), e);
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

  @VisibleForTesting
  List<PantheonPlugin> getPlugins() {
    return Collections.unmodifiableList(plugins);
  }

  private Optional<ClassLoader> pluginDirectoryLoader(final Path pluginsDir) {
    if (pluginsDir != null && pluginsDir.toFile().isDirectory()) {
      LOG.debug("Searching for plugins in {}", pluginsDir.toAbsolutePath().toString());

      try (final Stream<Path> pluginFilesList = Files.list(pluginsDir)) {
        final URL[] pluginJarURLs =
            pluginFilesList
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .map(PantheonPluginContextImpl::pathToURIOrNull)
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
}
