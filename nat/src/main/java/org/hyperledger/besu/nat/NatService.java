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
package org.hyperledger.besu.nat;

import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utility class to help interacting with various {@link NatManager}. */
public class NatService {

  protected static final Logger LOG = LogManager.getLogger();

  private final NatMethod currentNatMethod;
  private Optional<NatManager> currentNatManager;

  public NatService(final NatMethod natMethod) {
    this.currentNatMethod = natMethod;
    this.currentNatManager = buildNatSystem(currentNatMethod);
  }

  /**
   * Returns whether or not the Besu node is running under a NAT environment.
   *
   * @return true if Besu node is running under NAT environment, false otherwise.
   */
  public boolean isNatEnvironment() {
    return currentNatMethod != NatMethod.NONE;
  }

  /**
   * Returns the NAT method.
   *
   * @return an {@link Optional} wrapping the {@link NatMethod} or empty if not found.
   */
  public NatMethod getNatMethod() {
    return currentNatMethod;
  }

  /**
   * Returns the NAT system associated to the current NAT method.
   *
   * @return an {@link Optional} wrapping the {@link NatManager} or empty if not found.
   */
  public Optional<NatManager> getNatManager() {
    return currentNatManager;
  }

  /** Starts the system or service. */
  public void start() {
    if (isNatEnvironment()) {
      try {
        getNatManager().orElseThrow().start();
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to start the system or service", e);
      }
    }
  }

  /** Stops the system or service. */
  public void stop() {
    if (isNatEnvironment()) {
      try {
        getNatManager().orElseThrow().stop();
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to stop the system or service", e);
      }
    }
  }

  /**
   * Returns a {@link Optional} wrapping the advertised IP address.
   *
   * @return The advertised IP address wrapped in a {@link Optional}. Empty if
   *     `isNatExternalIpUsageEnabled` is false
   */
  public Optional<String> queryExternalIPAddress() {
    if (isNatEnvironment()) {
      try {
        final NatManager natSystem = getNatManager().orElseThrow();
        LOG.info(
            "Waiting for up to {} seconds to detect external IP address...",
            NatManager.TIMEOUT_SECONDS);
        return Optional.of(
            natSystem.queryExternalIPAddress().get(NatManager.TIMEOUT_SECONDS, TimeUnit.SECONDS));

      } catch (Exception e) {
        LOG.warn(
            "Caught exception while trying to query NAT external IP address (ignoring): {}", e);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns a {@link Optional} wrapping the local IP address.
   *
   * @return The local IP address wrapped in a {@link Optional}.
   */
  public Optional<String> queryLocalIPAddress() throws RuntimeException {
    if (isNatEnvironment()) {
      try {
        final NatManager natSystem = getNatManager().orElseThrow();
        LOG.info(
            "Waiting for up to {} seconds to detect external IP address...",
            NatManager.TIMEOUT_SECONDS);
        return Optional.of(
            natSystem.queryLocalIPAddress().get(NatManager.TIMEOUT_SECONDS, TimeUnit.SECONDS));
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to query local IP address (ignoring): {}", e);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the port mapping associated to the passed service type.
   *
   * @param serviceType The service type {@link NatServiceType}.
   * @param networkProtocol The network protocol {@link NetworkProtocol}.
   * @return The port mapping {@link NatPortMapping}
   */
  public Optional<NatPortMapping> getPortMapping(
      final NatServiceType serviceType, final NetworkProtocol networkProtocol) {
    if (isNatEnvironment()) {
      try {
        final NatManager natSystem = getNatManager().orElseThrow();
        return Optional.of(natSystem.getPortMapping(serviceType, networkProtocol));
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to query port mapping (ignoring): {}", e);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  void setNatSystem(final NatManager natManager) {
    this.currentNatManager = Optional.of(natManager);
  }

  /** Initialize the current NatMethod. */
  private Optional<NatManager> buildNatSystem(final NatMethod givenNatMethod) {
    switch (givenNatMethod) {
      case UPNP:
        return Optional.of(new UpnpNatManager());
      case NONE:
      default:
        return Optional.empty();
    }
  }
}
