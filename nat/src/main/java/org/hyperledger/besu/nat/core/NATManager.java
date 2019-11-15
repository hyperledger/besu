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
import org.hyperledger.besu.nat.upnp.UpnpNatSystem;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utility class to help interacting with various {@link NATSystem}. */
public class NATManager {
  protected static final Logger LOG = LogManager.getLogger();

  private final NATMethod currentNatMethod;
  private final Optional<NATSystem> currentNatSystem;

  public NATManager(final NATMethod natMethod) {
    this.currentNatMethod = natMethod;
    switch (currentNatMethod) {
      case UPNP:
        currentNatSystem = Optional.of(new UpnpNatSystem());
        break;
      case NONE:
      default:
        currentNatSystem = Optional.empty();
    }
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
    return currentNatSystem;
  }
}
