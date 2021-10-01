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
package org.hyperledger.besu.ethereum.eth.sync;

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.ethereum.eth.manager.ChainState;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer.Responder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Arrays;
import java.util.Collection;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ChainHeadTrackerTest {

  private BlockchainSetupUtil blockchainSetupUtil;
  private MutableBlockchain blockchain;
  private EthProtocolManager ethProtocolManager;
  private RespondingEthPeer respondingPeer;
  private ChainHeadTracker chainHeadTracker;

  private final ProtocolSchedule protocolSchedule =
      FixedDifficultyProtocolSchedule.create(
          GenesisConfigFile.development().getConfigOptions(), false, EvmConfiguration.DEFAULT);

  private final TrailingPeerLimiter trailingPeerLimiter = mock(TrailingPeerLimiter.class);

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {{DataStorageFormat.BONSAI}, {DataStorageFormat.FOREST}});
  }

  private final DataStorageFormat storageFormat;

  public ChainHeadTrackerTest(final DataStorageFormat storageFormat) {
    this.storageFormat = storageFormat;
  }

  @Before
  public void setup() {
    blockchainSetupUtil = BlockchainSetupUtil.forTesting(storageFormat);
    blockchain = blockchainSetupUtil.getBlockchain();
    ethProtocolManager = EthProtocolManagerTestUtil.create(blockchain);
    respondingPeer =
        RespondingEthPeer.builder()
            .ethProtocolManager(ethProtocolManager)
            .chainHeadHash(blockchain.getChainHeadHash())
            .totalDifficulty(blockchain.getChainHead().getTotalDifficulty())
            .estimatedHeight(0)
            .build();
    chainHeadTracker =
        new ChainHeadTracker(
            ethProtocolManager.ethContext(),
            protocolSchedule,
            trailingPeerLimiter,
            new NoOpMetricsSystem());
  }

  @Test
  public void shouldRequestHeaderChainHeadWhenNewPeerConnects() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchainSetupUtil.getBlockchain(),
            blockchainSetupUtil.getWorldArchive(),
            blockchainSetupUtil.getTransactionPool());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    Assertions.assertThat(chainHeadState().getEstimatedHeight()).isZero();

    respondingPeer.respond(responder);

    Assertions.assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  @Test
  public void shouldIgnoreHeadersIfChainHeadHasAlreadyBeenUpdatedWhileWaiting() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchainSetupUtil.getBlockchain(),
            blockchainSetupUtil.getWorldArchive(),
            blockchainSetupUtil.getTransactionPool());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    // Change the hash of the current known head
    respondingPeer.getEthPeer().chainState().statusReceived(Hash.EMPTY_TRIE_HASH, Difficulty.ONE);

    respondingPeer.respond(responder);

    Assertions.assertThat(chainHeadState().getEstimatedHeight()).isZero();
  }

  @Test
  public void shouldCheckTrialingPeerLimits() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(
            blockchainSetupUtil.getBlockchain(),
            blockchainSetupUtil.getWorldArchive(),
            blockchainSetupUtil.getTransactionPool());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    Assertions.assertThat(chainHeadState().getEstimatedHeight()).isZero();

    respondingPeer.respond(responder);

    Assertions.assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  private ChainState chainHeadState() {
    return respondingPeer.getEthPeer().chainState();
  }
}
