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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.core.domain.NatPortMapping;
import org.hyperledger.besu.nat.core.domain.NatServiceType;
import org.hyperledger.besu.nat.core.domain.NetworkProtocol;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utility class to help interacting with various {@link NatManager}. */
public class NatService {

  protected static final Logger LOG = LogManager.getLogger();

  private NatMethod currentNatMethod;
  private Optional<NatManager> currentNatManager;

  public NatService(final Optional<NatManager> natManager) {
    this.currentNatMethod = retrieveNatMethod(natManager);
    this.currentNatManager = natManager;
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
   * @return the current NatMethod.
   */
  public NatMethod getNatMethod() {
    return currentNatMethod;
  }

  /**
   * Returns the NAT manager associated to the current NAT method.
   *
   * @return an {@link Optional} wrapping the {@link NatManager} or empty if not found.
   */
  public Optional<NatManager> getNatManager() {
    return currentNatManager;
  }

  /** Starts the manager or service. */
  public void start() {
    if (isNatEnvironment()) {
      try {
        getNatManager().orElseThrow().start();
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to start the manager or service", e);
      }
    }
  }

  /** Stops the manager or service. */
  public void stop() {
    if (isNatEnvironment()) {
      try {
        getNatManager().orElseThrow().stop();
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to stop the manager or service", e);
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
        final NatManager natManager = getNatManager().orElseThrow();
        LOG.info(
            "Waiting for up to {} seconds to detect external IP address...",
            NatManager.TIMEOUT_SECONDS);
        return Optional.of(
            natManager.queryExternalIPAddress().get(NatManager.TIMEOUT_SECONDS, TimeUnit.SECONDS));

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
        final NatManager natManager = getNatManager().orElseThrow();
        LOG.info(
            "Waiting for up to {} seconds to detect external IP address...",
            NatManager.TIMEOUT_SECONDS);
        return Optional.of(
            natManager.queryLocalIPAddress().get(NatManager.TIMEOUT_SECONDS, TimeUnit.SECONDS));
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
        final NatManager natManager = getNatManager().orElseThrow();
        return Optional.of(natManager.getPortMapping(serviceType, networkProtocol));
      } catch (Exception e) {
        LOG.warn("Caught exception while trying to query port mapping (ignoring): {}", e);
      }
    }
    return Optional.empty();
  }

  /**
   * Retrieve the current NatMethod.
   *
   * @param natManager The natManager wrapped in a {@link Optional}.
   * @return the current NatMethod.
   */
  private NatMethod retrieveNatMethod(final Optional<NatManager> natManager) {
    return natManager.map(NatManager::getNatMethod).orElse(NatMethod.NONE);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Optional<NatManager> natManager = Optional.empty();

    public Builder natManager(final Optional<NatManager> natManager) {
      checkNotNull(natManager);
      this.natManager = natManager;
      return this;
    }

    public NatService build() {
      return new NatService(natManager);
    }
  }
}
