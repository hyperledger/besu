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
package org.hyperledger.besu.consensus.ibftlegacy.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.DeterministicEthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection;
import org.hyperledger.besu.ethereum.eth.manager.MockPeerConnection.PeerSendHandler;
import org.hyperledger.besu.ethereum.eth.messages.BlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.StatusMessage;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

public class Istanbul99ProtocolManagerTest {

  private static Blockchain blockchain;
  private static TransactionPool transactionPool;
  private static ProtocolSchedule protocolSchedule;
  private static ProtocolContext protocolContext;

  @BeforeClass
  public static void setup() {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.FOREST);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    transactionPool = blockchainSetupUtil.getTransactionPool();
    protocolSchedule = blockchainSetupUtil.getProtocolSchedule();
    protocolContext = blockchainSetupUtil.getProtocolContext();
    assertThat(blockchainSetupUtil.getMaxBlockNumber()).isGreaterThanOrEqualTo(20L);
  }

  private MockPeerConnection setupPeer(
      final EthProtocolManager ethManager, final PeerSendHandler onSend) {
    final MockPeerConnection peer = setupPeerWithoutStatusExchange(ethManager, onSend);
    final StatusMessage statusMessage =
        StatusMessage.create(
            EthProtocol.EthVersion.V63,
            BigInteger.ONE,
            blockchain.getChainHead().getTotalDifficulty(),
            blockchain.getChainHeadHash(),
            blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get().getHash());
    ethManager.processMessage(
        Istanbul99Protocol.ISTANBUL99, new DefaultMessage(peer, statusMessage));
    return peer;
  }

  private MockPeerConnection setupPeerWithoutStatusExchange(
      final EthProtocolManager ethManager, final PeerSendHandler onSend) {
    final Set<Capability> caps =
        new HashSet<>(Collections.singletonList(Istanbul99Protocol.ISTANBUL99));
    final MockPeerConnection peer = new MockPeerConnection(caps, onSend);
    ethManager.handleNewConnection(peer);
    return peer;
  }

  @Test
  public void respondToEth65GetHeadersUsingIstanbul99()
      throws ExecutionException, InterruptedException, TimeoutException {
    final CompletableFuture<Void> done = new CompletableFuture<>();
    final EthScheduler ethScheduler = new DeterministicEthScheduler(() -> false);
    EthPeers peers =
        new EthPeers(
            Istanbul99Protocol.NAME,
            TestClock.fixed(),
            new NoOpMetricsSystem(),
            25,
            EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE);
    EthMessages messages = new EthMessages();

    final BigInteger networkId = BigInteger.ONE;
    try (final EthProtocolManager ethManager =
        new Istanbul99ProtocolManager(
            blockchain,
            networkId,
            protocolContext.getWorldStateArchive(),
            transactionPool,
            EthProtocolConfiguration.defaultConfig(),
            peers,
            messages,
            new EthContext(peers, messages, ethScheduler),
            Collections.emptyList(),
            false,
            ethScheduler)) {

      final long startBlock = blockchain.getChainHeadBlockNumber() + 1;
      final int blockCount = 5;
      final MessageData messageData =
          GetBlockHeadersMessage.create(startBlock, blockCount, 0, false);
      final PeerSendHandler onSend =
          (cap, message, conn) -> {
            if (message.getCode() == EthPV62.STATUS) {
              // Ignore status message
              return;
            }
            assertThat(message.getCode()).isEqualTo(EthPV62.BLOCK_HEADERS);
            final BlockHeadersMessage headersMsg = BlockHeadersMessage.readFrom(message);
            final List<BlockHeader> headers =
                Lists.newArrayList(headersMsg.getHeaders(protocolSchedule));
            assertThat(headers.size()).isEqualTo(0);
            done.complete(null);
          };
      final PeerConnection peer = setupPeer(ethManager, onSend);
      ethManager.processMessage(
          Istanbul99Protocol.ISTANBUL99, new DefaultMessage(peer, messageData));
      done.get(10, TimeUnit.SECONDS);
    }
  }
}
