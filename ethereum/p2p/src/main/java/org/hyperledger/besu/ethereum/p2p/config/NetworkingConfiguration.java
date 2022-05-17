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

import java.util.Objects;
import java.util.Optional;

public class NetworkingConfiguration {
  public static final int DEFAULT_INITIATE_CONNECTIONS_FREQUENCY_SEC = 30;
  public static final int DEFAULT_CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_SEC = 60;

  private DiscoveryConfiguration discovery = new DiscoveryConfiguration();
  private RlpxConfiguration rlpx = new RlpxConfiguration();
  private int initiateConnectionsFrequencySec = DEFAULT_INITIATE_CONNECTIONS_FREQUENCY_SEC;
  private int checkMaintainedConnectionsFrequencySec =
      DEFAULT_CHECK_MAINTAINED_CONNECTIONS_FREQUENCY_SEC;
  private Optional<String> dnsDiscoveryServerOverride = Optional.empty();

  public static NetworkingConfiguration create() {
    return new NetworkingConfiguration();
  }

  public DiscoveryConfiguration getDiscovery() {
    return discovery;
  }

  public NetworkingConfiguration setDiscovery(final DiscoveryConfiguration discovery) {
    this.discovery = discovery;
    return this;
  }

  public RlpxConfiguration getRlpx() {
    return rlpx;
  }

  public NetworkingConfiguration setRlpx(final RlpxConfiguration rlpx) {
    this.rlpx = rlpx;
    return this;
  }

  public int getInitiateConnectionsFrequencySec() {
    return initiateConnectionsFrequencySec;
  }

  public NetworkingConfiguration setInitiateConnectionsFrequency(
      final int initiateConnectionsFrequency) {
    checkArgument(initiateConnectionsFrequency > 0);
    this.initiateConnectionsFrequencySec = initiateConnectionsFrequency;
    return this;
  }

  public int getCheckMaintainedConnectionsFrequencySec() {
    return checkMaintainedConnectionsFrequencySec;
  }

  public NetworkingConfiguration setDnsDiscoveryServerOverride(
      final Optional<String> dnsDiscoveryServerOverride) {
    this.dnsDiscoveryServerOverride = dnsDiscoveryServerOverride;
    return this;
  }

  public Optional<String> getDnsDiscoveryServerOverride() {
    return dnsDiscoveryServerOverride;
  }

  public NetworkingConfiguration setCheckMaintainedConnectionsFrequency(
      final int checkMaintainedConnectionsFrequency) {
    checkArgument(checkMaintainedConnectionsFrequency > 0);
    this.checkMaintainedConnectionsFrequencySec = checkMaintainedConnectionsFrequency;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof NetworkingConfiguration)) {
      return false;
    }
    final NetworkingConfiguration that = (NetworkingConfiguration) o;
    return Objects.equals(discovery, that.discovery) && Objects.equals(rlpx, that.rlpx);
  }

  @Override
  public int hashCode() {
    return Objects.hash(discovery, rlpx);
  }

  @Override
  public String toString() {
    return "NetworkingConfiguration{" + "discovery=" + discovery + ", rlpx=" + rlpx + '}';
  }
}
