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
import org.hyperledger.besu.nat.upnp.UpnpNatSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utility class to help interacting with various {@link NATSystem}. */
public class NATManager {

  protected static final Logger LOG = LogManager.getLogger();
  private static final Map<NATMethod, NATSystem> AVAILABLE_NAT_SYSTEMS = new HashMap<>();

  static {
    AVAILABLE_NAT_SYSTEMS.put(NATMethod.UPNP, new UpnpNatSystem());
  }

  private final NATMethod currentNatMethod;
  private final boolean natExternalIpUsageEnabled;

  public NATManager(final NATMethod natMethod) {
    this(natMethod, true);
  }

  public NATManager(final NATMethod natMethod, final boolean natExternalIpUsageEnabled) {
    this.natExternalIpUsageEnabled = natExternalIpUsageEnabled;
    this.currentNatMethod = initializeNatMethod(natMethod);
  }

  /**
   * Returns whether or not the Besu node is running under a NAT environment.
   *
   * @return true if Besu node is running under NAT environment, false otherwise.
   */
  public boolean isNATEnvironment() {
    return currentNatMethod != NATMethod.NONE;
  }

  /**
   * Returns whether or not the nat external IP usage is enabled.
   *
   * @return true if the usage of the nat external ip is enabled, false otherwise.
   */
  public boolean isNatExternalIpUsageEnabled() {
    return natExternalIpUsageEnabled;
  }

  /**
   * Returns the NAT method.
   *
   * @return an {@link Optional} wrapping the {@link NATMethod} or empty if not found.
   */
  public NATMethod getNatMethod() {
    return currentNatMethod;
  }

  /**
   * Returns the NAT system associated to the current NAT method.
   *
   * @return an {@link Optional} wrapping the {@link NATSystem} or empty if not found.
   */
  public Optional<NATSystem> getNatSystem() {
    return Optional.ofNullable(AVAILABLE_NAT_SYSTEMS.get(currentNatMethod));
  }

  /**
   * Returns a {@link Optional} wrapping the advertised IP address.
   *
   * @return The advertised IP address wrapped in a {@link Optional}. Empty if
   *     `isNatExternalIpUsageEnabled` is false
   */
  public Optional<String> getAdvertisedIp() {
    if (isNATEnvironment() && isNatExternalIpUsageEnabled()) {
      try {
        final NATSystem natSystem = getNatSystem().get();
        LOG.info(
            "Waiting for up to {} seconds to detect external IP address...",
            NATSystem.TIMEOUT_SECONDS);
        return Optional.of(
            natSystem.getExternalIPAddress().get(NATSystem.TIMEOUT_SECONDS, TimeUnit.SECONDS));

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
  public Optional<String> getLocalIp() throws RuntimeException {
    if (isNATEnvironment()) {
      try {
        final NATSystem natSystem = getNatSystem().orElseThrow();
        LOG.info(
            "Waiting for up to {} seconds to detect external IP address...",
            NATSystem.TIMEOUT_SECONDS);
        return Optional.of(
            natSystem.getLocalIPAddress().get(NATSystem.TIMEOUT_SECONDS, TimeUnit.SECONDS));
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to query local IP address (ignoring): {}", e);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the port mapping associated to the passed service type.
   *
   * @param serviceType The service type {@link NATServiceType}.
   * @param networkProtocol The network protocol {@link NetworkProtocol}.
   * @return The port mapping {@link NATPortMapping}
   */
  public Optional<NATPortMapping> getPortMapping(
      final NATServiceType serviceType, final NetworkProtocol networkProtocol) {
    if (isNATEnvironment()) {
      try {
        final NATSystem natSystem = getNatSystem().orElseThrow();
        return Optional.of(natSystem.getPortMapping(serviceType, networkProtocol));
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to query port mapping (ignoring): {}", e);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  void setNatSystem(final NATSystem natSystem) {
    AVAILABLE_NAT_SYSTEMS.put(natSystem.getNatMethod(), natSystem);
  }

  /** Initialize the current NatMethod. */
  private NATMethod initializeNatMethod(final NATMethod givenNatMethod) {
    if (givenNatMethod.equals(NATMethod.NONE)) {
      // try to detect automatically the current nat method
      for (NATSystem natSystem : AVAILABLE_NAT_SYSTEMS.values()) {
        if (natSystem.isRunningOn()) return natSystem.getNatMethod();
      }
    }
    return givenNatMethod;
  }
}
