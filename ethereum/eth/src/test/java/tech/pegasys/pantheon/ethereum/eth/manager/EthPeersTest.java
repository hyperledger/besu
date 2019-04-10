/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.Before;
import org.junit.Test;

public class EthPeersTest {

  private EthProtocolManager ethProtocolManager;

  @Before
  public void setup() {
    ethProtocolManager = EthProtocolManagerTestUtil.create();
  }

  @Test
  public void comparesPeersWithHeightAndTd() {
    // Set peerA with better height, lower td
    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(50), 20).getEthPeer();
    final EthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(100), 10).getEthPeer();

    assertThat(EthPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isLessThan(0);

    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerB)).isLessThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerA)).isGreaterThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerA)).isEqualTo(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerB)).isEqualTo(0);
  }

  @Test
  public void comparesPeersWithTdAndNoHeight() {
    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(100), 0).getEthPeer();
    final EthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(50), 0).getEthPeer();

    // Sanity check
    assertThat(peerA.chainState().getEstimatedHeight()).isEqualTo(0);
    assertThat(peerB.chainState().getEstimatedHeight()).isEqualTo(0);

    assertThat(EthPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isEqualTo(0);
    assertThat(EthPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isGreaterThan(0);

    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerA)).isLessThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerA)).isEqualTo(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerB)).isEqualTo(0);
  }
}
