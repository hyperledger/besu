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
package org.hyperledger.besu.plugin;

import org.hyperledger.besu.plugin.services.BesuService;

import java.util.Optional;

/** Allows plugins to access Besu services. */
public interface BesuContext {

  /**
   * Get the requested service, if it is available. There are a number of reasons that a service may
   * not be available:
   *
   * <ul>
   *   <li>The service may not have started yet. Most services are not available before the {@link
   *       BesuPlugin#start()} method is called
   *   <li>The service is not supported by this version of Besu
   *   <li>The service may not be applicable to the current configuration. For example some services
   *       may only be available when a proof of authority network is in use
   * </ul>
   *
   * <p>Since plugins are automatically loaded, unless the user has specifically requested
   * functionality provided by the plugin, no error should be raised if required services are
   * unavailable.
   *
   * @param serviceType the class defining the requested service.
   * @param <T> the service type
   * @return an optional containing the instance of the requested service, or empty if the service
   *     is unavailable
   */
  <T extends BesuService> Optional<T> getService(Class<T> serviceType);
}
