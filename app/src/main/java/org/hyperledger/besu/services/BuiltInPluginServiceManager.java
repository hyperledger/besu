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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import picocli.CommandLine;

/**
 * The service manager handed to built-in plugins when they are registered.
 *
 * <p>It differs from the plugin context it wraps in two ways:
 *
 * <ul>
 *   <li>{@link PicoCLIOptions} is bound to Besu's command line and also accepts option names
 *       starting with the given additional prefixes, so a built-in plugin can keep the option names
 *       it had when it was part of Besu core (for example {@code --graphql-http-enabled}). External
 *       plugins keep getting the context's own {@link PicoCLIOptions}, which enforces the {@code
 *       --plugin-<namespace>-} prefix.
 *   <li>Services given with {@link #withService(Class, BesuService)} take precedence over the
 *       context's services. Besu uses this to hand its own instances to built-in plugins directly,
 *       which also keeps them working when the context is a test double.
 * </ul>
 *
 * <p>Everything else, including {@link #addService(Class, BesuService)}, goes to the wrapped
 * context, so services added by a built-in plugin are visible to every plugin.
 */
public class BuiltInPluginServiceManager implements ServiceManager {

  private final ServiceManager delegate;
  private final Map<Class<? extends BesuService>, BesuService> overrides =
      new ConcurrentHashMap<>();

  /**
   * Instantiates a new service manager for built-in plugins.
   *
   * @param delegate the plugin context to delegate to
   * @param commandLine Besu's command line, which built-in plugin options are added to
   * @param additionalAllowedPrefixes option name prefixes accepted besides the plugin prefixes, for
   *     example {@code "--graphql-"}
   */
  public BuiltInPluginServiceManager(
      final ServiceManager delegate,
      final CommandLine commandLine,
      final Set<String> additionalAllowedPrefixes) {
    this.delegate = delegate;
    overrides.put(
        PicoCLIOptions.class, new PicoCLIOptionsImpl(commandLine, additionalAllowedPrefixes));
  }

  /**
   * Make the given service available to built-in plugins, in preference to the one of the wrapped
   * context. The wrapped context is not changed.
   *
   * @param <T> the service type
   * @param serviceType the service type
   * @param service the service
   * @return this service manager
   * @throws NullPointerException if the service type or the service is null
   * @throws IllegalArgumentException if the service type is not an interface, or the service does
   *     not implement it
   */
  public <T extends BesuService> BuiltInPluginServiceManager withService(
      final Class<T> serviceType, final T service) {
    // same checks as BesuPluginContextImpl.addService
    requireNonNull(serviceType, "serviceType must not be null");
    requireNonNull(service, "service must not be null");
    checkArgument(serviceType.isInterface(), "Services must be Java interfaces.");
    checkArgument(
        serviceType.isInstance(service),
        "The service registered with a type must implement that type");
    overrides.put(serviceType, service);
    return this;
  }

  @Override
  public <T extends BesuService> void addService(final Class<T> serviceType, final T service) {
    delegate.addService(serviceType, service);
  }

  @Override
  public <T extends BesuService> Optional<T> getService(final Class<T> serviceType) {
    final BesuService override = overrides.get(serviceType);
    if (override != null) {
      return Optional.of(serviceType.cast(override));
    }
    return delegate.getService(serviceType);
  }
}
