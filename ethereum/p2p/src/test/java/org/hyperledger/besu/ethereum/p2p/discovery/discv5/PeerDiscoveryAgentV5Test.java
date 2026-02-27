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
package org.hyperledger.besu.ethereum.p2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.ImmutableNetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.discovery.NodeRecordManager;
import org.hyperledger.besu.ethereum.p2p.discovery.discv4.internal.DiscoveryPeerV4;
import org.hyperledger.besu.ethereum.p2p.rlpx.RlpxAgent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.ethereum.beacon.discovery.MutableDiscoverySystem;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeerDiscoveryAgentV5Test {

  @Mock private RlpxAgent rlpxAgent;
  @Mock private ForkIdManager forkIdManager;
  @Mock private NodeRecordManager nodeRecordManager;
  @Mock private MutableDiscoverySystem mockSystem;
  @Mock private DiscoveryPeerV4 localPeer;
  @Mock private NodeRecord localNodeRecord;

  private NetworkingConfiguration config;
  private PeerDiscoveryAgentV5 agent;

  @BeforeEach
  void setUp() {
    config =
        ImmutableNetworkingConfiguration.builder()
            .discoveryConfiguration(
                DiscoveryConfiguration.create()
                    .setEnabled(true)
                    .setAdvertisedHost("127.0.0.1")
                    .setBindHost("0.0.0.0")
                    .setBindPort(0))
            .build();

    // Set up the nodeRecordManager mock chain for initializeLocalNodeRecord
    lenient().when(rlpxAgent.getIpv6ListeningPort()).thenReturn(Optional.empty());
    lenient().when(nodeRecordManager.getLocalNode()).thenReturn(Optional.of(localPeer));
    lenient().when(localPeer.getNodeRecord()).thenReturn(Optional.of(localNodeRecord));

    // Set up mock system to return localNodeRecord with UDP address
    lenient().when(mockSystem.getLocalNodeRecord()).thenReturn(localNodeRecord);
    lenient()
        .when(localNodeRecord.getUdpAddress())
        .thenReturn(Optional.of(new InetSocketAddress(InetAddress.getLoopbackAddress(), 30303)));

    agent =
        new PeerDiscoveryAgentV5(
            config,
            forkIdManager,
            nodeRecordManager,
            rlpxAgent,
            false,
            (nodeRecord, listener) -> mockSystem);
  }

  @AfterEach
  void tearDown() {
    agent.stop();
  }

  @Test
  void startTwice_secondCallFails() throws Exception {
    when(mockSystem.start()).thenReturn(CompletableFuture.completedFuture(null));

    final CompletableFuture<Integer> first = agent.start(1234);
    assertThat(first.get()).isEqualTo(30303);

    final CompletableFuture<Integer> second = agent.start(1234);
    assertThat(second).isCompletedExceptionally();
    assertThat(second)
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("already started");
  }

  @Test
  void schedulerStartsOnlyAfterSystemStartCompletes() {
    final CompletableFuture<Void> startFuture = new CompletableFuture<>();
    when(mockSystem.start()).thenReturn(startFuture);
    lenient().when(rlpxAgent.getConnectionCount()).thenReturn(0);
    lenient().when(rlpxAgent.getMaxPeers()).thenReturn(25);
    lenient()
        .when(mockSystem.searchForNewPeers())
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    agent.start(1234);

    // system.start() hasn't completed yet — scheduler should not have fired
    verify(mockSystem, never()).searchForNewPeers();

    // Complete system.start() — scheduler should now start and fire discovery
    startFuture.complete(null);

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(mockSystem, atLeastOnce()).searchForNewPeers());
  }

  @Test
  void schedulerShutdownOnStartFailure() {
    when(mockSystem.start())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("bind failed")));

    final CompletableFuture<Integer> result = agent.start(1234);
    assertThat(result).isCompletedExceptionally();
    assertThat(agent.getScheduler().isShutdown()).isTrue();
  }

  @Test
  void startWhenDisabled_returnsZero() throws Exception {
    final NetworkingConfiguration disabledConfig =
        ImmutableNetworkingConfiguration.builder()
            .discoveryConfiguration(DiscoveryConfiguration.create().setEnabled(false))
            .build();

    final PeerDiscoveryAgentV5 disabledAgent =
        new PeerDiscoveryAgentV5(
            disabledConfig,
            forkIdManager,
            nodeRecordManager,
            rlpxAgent,
            false,
            (nodeRecord, listener) -> mockSystem);

    final CompletableFuture<Integer> result = disabledAgent.start(1234);
    assertThat(result.get()).isEqualTo(0);

    // Verify no interaction with the discovery system
    verify(mockSystem, never()).start();
  }
}
