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
package org.hyperledger.besu.ethereum.p2p.config;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Duration;
import java.util.Optional;

import org.immutables.value.Value;

@Value.Immutable
public interface NetworkingConfiguration {
  Duration DEFAULT_INITIATE_CONNECTIONS_FREQUENCY = Duration.ofSeconds(30);
  Duration DEFAULT_CHECK_MAINTAINED_CONNECTIONS_FREQUENCY = Duration.ofSeconds(60);
  boolean DEFAULT_FILTER_ON_ENR_FORK_ID = true;

  NetworkingConfiguration DEFAULT = ImmutableNetworkingConfiguration.builder().build();

  @Value.Default
  default DiscoveryConfiguration discoveryConfiguration() {
    return new DiscoveryConfiguration();
  }

  @Value.Default
  default RlpxConfiguration rlpxConfiguration() {
    return new RlpxConfiguration();
  }

  @Value.Default
  default Duration initiateConnectionsFrequency() {
    return DEFAULT_INITIATE_CONNECTIONS_FREQUENCY;
  }

  @Value.Default
  default Duration checkMaintainedConnectionsFrequency() {
    return DEFAULT_CHECK_MAINTAINED_CONNECTIONS_FREQUENCY;
  }

  Optional<String> dnsDiscoveryServerOverride();

  @Value.Check
  default void check() {
    checkArgument(
        initiateConnectionsFrequency().isPositive(),
        "initiateConnectionsFrequency must be positive");
    checkArgument(
        checkMaintainedConnectionsFrequency().isPositive(),
        "checkMaintainedConnectionsFrequency must be positive");
  }
}
