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
package tech.pegasys.pantheon.ethereum.eth.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.Test;

public class ChainHeadTrackerTest {

  private final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
  private final MutableBlockchain blockchain = blockchainSetupUtil.getBlockchain();
  private final EthProtocolManager ethProtocolManager =
      EthProtocolManagerTestUtil.create(blockchain, blockchainSetupUtil.getWorldArchive());
  private final RespondingEthPeer respondingPeer =
      RespondingEthPeer.create(
          ethProtocolManager,
          blockchain.getChainHeadHash(),
          blockchain.getChainHead().getTotalDifficulty(),
          0);
  private final ProtocolSchedule<Void> protocolSchedule =
      FixedDifficultyProtocolSchedule.create(
          GenesisConfigFile.development().getConfigOptions(), false);

  private final TrailingPeerLimiter trailingPeerLimiter = mock(TrailingPeerLimiter.class);
  private final ChainHeadTracker chainHeadTracker =
      new ChainHeadTracker(
          ethProtocolManager.ethContext(),
          protocolSchedule,
          trailingPeerLimiter,
          new NoOpMetricsSystem());

  @Test
  public void shouldRequestHeaderChainHeadWhenNewPeerConnects() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchainSetupUtil.getBlockchain(), blockchainSetupUtil.getWorldArchive());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    assertThat(chainHeadState().getEstimatedHeight()).isZero();

    respondingPeer.respond(responder);

    assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  @Test
  public void shouldIgnoreHeadersIfChainHeadHasAlreadyBeenUpdatedWhileWaiting() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchainSetupUtil.getBlockchain(), blockchainSetupUtil.getWorldArchive());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    // Change the hash of the current known head
    respondingPeer.getEthPeer().chainState().statusReceived(Hash.EMPTY_TRIE_HASH, UInt256.ONE);

    respondingPeer.respond(responder);

    assertThat(chainHeadState().getEstimatedHeight()).isZero();
  }

  @Test
  public void shouldCheckTrialingPeerLimits() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchainSetupUtil.getBlockchain(), blockchainSetupUtil.getWorldArchive());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    assertThat(chainHeadState().getEstimatedHeight()).isZero();

    respondingPeer.respond(responder);

    assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  private ChainState chainHeadState() {
    return respondingPeer.getEthPeer().chainState();
  }
}
