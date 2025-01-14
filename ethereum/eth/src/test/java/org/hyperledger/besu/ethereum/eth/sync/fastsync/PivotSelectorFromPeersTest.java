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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PivotSelectorFromPeersTest {
  private @Mock EthContext ethContext;
  private @Mock EthPeers ethPeers;
  private @Mock SyncState syncState;

  private PivotSelectorFromPeers selector;

  @BeforeEach
  public void beforeTest() {
    SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().syncMinimumPeerCount(2).syncPivotDistance(1).build();

    selector = new PivotSelectorFromPeers(ethContext, syncConfig, syncState);
  }

  @Test
  public void testSelectNewPivotBlock() {
    EthPeer peer1 = mockPeer(true, 10, true);
    EthPeer peer2 = mockPeer(true, 8, true);

    Mockito.when(ethContext.getEthPeers()).thenReturn(ethPeers);
    Mockito.when(ethPeers.streamAvailablePeers()).thenReturn(Stream.of(peer1, peer2));
    Mockito.when(ethPeers.getBestPeerComparator())
        .thenReturn((p1, ignored) -> p1 == peer1 ? 1 : -1);

    Optional<FastSyncState> result = selector.selectNewPivotBlock();

    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals(9, result.get().getPivotBlockNumber().getAsLong());
  }

  @Test
  public void testSelectNewPivotBlockWithInsufficientPeers() {
    Mockito.when(ethContext.getEthPeers()).thenReturn(ethPeers);
    Mockito.when(ethPeers.streamAvailablePeers()).thenReturn(Stream.empty());

    Optional<FastSyncState> result = selector.selectNewPivotBlock();

    Assertions.assertTrue(result.isEmpty());
  }

  private EthPeer mockPeer(
      final boolean hasEstimatedHeight, final long chainHeight, final boolean isFullyValidated) {
    EthPeer ethPeer = Mockito.mock(EthPeer.class);
    ChainState chainState = Mockito.mock(ChainState.class);
    Mockito.when(ethPeer.chainState()).thenReturn(chainState);
    Mockito.when(chainState.hasEstimatedHeight()).thenReturn(hasEstimatedHeight);
    Mockito.when(chainState.getEstimatedHeight()).thenReturn(chainHeight);
    Mockito.when(ethPeer.isFullyValidated()).thenReturn(isFullyValidated);

    return ethPeer;
  }
}
