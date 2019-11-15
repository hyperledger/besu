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
package org.hyperledger.besu.nat.core;

import org.hyperledger.besu.nat.core.domain.NATMethod;
import org.hyperledger.besu.nat.core.domain.NATPortMapping;
import org.hyperledger.besu.nat.core.domain.NATServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This class describes the behaviour of any supported NAT system. Internal API to support Network
 * Address Translation (NAT) technologies in Besu.
 */
public interface NATSystem {

  int TIMEOUT_SECONDS = 60;

  /**
   * Returns the NAT method associated to this system.
   *
   * @return the {@link NATMethod}
   */
  NATMethod getNatMethod();

  /** Starts the system or service. */
  void start();

  /** Stops the system or service. */
  void stop();

  /**
   * Returns whether or not the system is started.
   *
   * @return true if started, false otherwise.
   */
  boolean isStarted();

  /**
   * Checks if the system is started and throws an {@link IllegalStateException} in case it is not
   * started. Convenient method to perform actions only if service is started.
   */
  default void requireSystemStarted() {
    if (!isStarted()) {
      throw new IllegalStateException("NAT system must be started.");
    }
  }

  /**
   * Returns a {@link java.util.concurrent.Future} wrapping the local IP address.
   *
   * @return The local IP address wrapped in a {@link java.util.concurrent.Future}.
   */
  CompletableFuture<String> getLocalIPAddress();

  /**
   * Returns a {@link java.util.concurrent.Future} wrapping the external IP address.
   *
   * @return The external IP address wrapped in a {@link java.util.concurrent.Future}.
   */
  CompletableFuture<String> getExternalIPAddress();

  /**
   * Returns all known port mappings.
   *
   * @return The known port mappings wrapped in a {@link java.util.concurrent.Future}.
   */
  CompletableFuture<List<NATPortMapping>> getPortMappings();

  /**
   * Returns the port mapping associated to the passed service type.
   *
   * @param serviceType The service type {@link NATServiceType}.
   * @param networkProtocol The network protocol {@link NetworkProtocol}.
   * @return The port mapping {@link NATPortMapping}
   */
  NATPortMapping getPortMapping(
      final NATServiceType serviceType, final NetworkProtocol networkProtocol);
}
