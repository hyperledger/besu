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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class RlpxConfigurationTest {

  @Test
  public void getMaxRemotelyInitiatedConnections_remoteLimitsDisabled() {
    final RlpxConfiguration config =
        RlpxConfiguration.create()
            .setFractionRemoteWireConnectionsAllowed(.5f)
            .setLimitRemoteWireConnectionsEnabled(false)
            .setMaxPeers(20);

    assertThat(config.getMaxRemotelyInitiatedConnections()).isEqualTo(20);
  }

  @Test
  public void getMaxRemotelyInitiatedConnections_remoteLimitsEnabled() {
    final RlpxConfiguration config =
        RlpxConfiguration.create()
            .setFractionRemoteWireConnectionsAllowed(.5f)
            .setLimitRemoteWireConnectionsEnabled(true)
            .setMaxPeers(20);

    assertThat(config.getMaxRemotelyInitiatedConnections()).isEqualTo(10);
  }

  @Test
  public void getMaxRemotelyInitiatedConnections_remoteLimitsEnabledWithNonIntegerRatio() {
    final RlpxConfiguration config =
        RlpxConfiguration.create()
            .setFractionRemoteWireConnectionsAllowed(.5f)
            .setLimitRemoteWireConnectionsEnabled(true)
            .setMaxPeers(25);

    assertThat(config.getMaxRemotelyInitiatedConnections()).isEqualTo(12);
  }

  @Test
  public void getMaxRemotelyInitiatedConnections_remoteLimitsEnabledRoundsToZero() {
    final RlpxConfiguration config =
        RlpxConfiguration.create()
            .setFractionRemoteWireConnectionsAllowed(.5f)
            .setLimitRemoteWireConnectionsEnabled(true)
            .setMaxPeers(1);

    assertThat(config.getMaxRemotelyInitiatedConnections()).isEqualTo(0);
  }
}
