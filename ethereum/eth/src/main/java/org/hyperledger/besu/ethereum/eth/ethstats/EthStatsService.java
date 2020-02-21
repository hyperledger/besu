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
package org.hyperledger.besu.ethereum.eth.ethstats;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.TimeoutStream;
import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.eth.Address;
import org.apache.tuweni.eth.Hash;
import org.apache.tuweni.ethstats.BlockStats;
import org.apache.tuweni.ethstats.EthStatsReporter;
import org.apache.tuweni.ethstats.NodeStats;
import org.apache.tuweni.ethstats.TxStats;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.ethereum.Gas;
import org.logl.log4j2.Log4j2LoggerProvider;

public class EthStatsService<C> {

  private static final long NODE_REPORTS_PERIOD = 10000;
  private static final long TX_REPORTS_PERIOD = 10000;

  private final EthStatsParameters ethStatsParameters;
  private final String networkInfo;
  private final BigInteger networkId;
  private final BlockBroadcaster blockBroadcaster;
  private final ProtocolContext<C> protocolContext;
  private final boolean miningEnabled;
  private final EthPeers ethPeers;
  private final SyncState syncState;
  private final TransactionPool transactionPool;

  private EthStatsReporter reporter;
  private long blockSubscriberId;
  private TimeoutStream nodeReporting;
  private TimeoutStream txReporting;

  public EthStatsService(
      final EthStatsParameters ethStatsParameters,
      final BigInteger networkId,
      final String networkInfo,
      final BlockBroadcaster blockBroadcaster,
      final ProtocolContext<C> protocolContext,
      final EthPeers ethPeers,
      final SyncState syncState,
      final boolean miningEnabled,
      final TransactionPool transactionPool) {
    this.ethStatsParameters = ethStatsParameters;
    this.networkId = networkId;
    this.networkInfo = networkInfo;
    this.blockBroadcaster = blockBroadcaster;
    this.protocolContext = protocolContext;
    this.ethPeers = ethPeers;
    this.syncState = syncState;
    this.miningEnabled = miningEnabled;
    this.transactionPool = transactionPool;
  }

  BlockStats convertToStats(final Block block, final Difficulty difficulty) {
    return convertToStats(
        block.getHeader(),
        block.getBody().getTransactions(),
        block.getBody().getOmmers(),
        difficulty);
  }

  BlockStats convertToStats(
      final BlockHeader header,
      final List<Transaction> txs,
      final List<BlockHeader> ommers,
      final Difficulty difficulty) {
    return new BlockStats(
        UInt256.valueOf(header.getNumber()),
        Hash.fromBytes(header.getHash()),
        Hash.fromBytes(header.getParentHash()),
        header.getTimestamp(),
        Address.fromBytes(header.getCoinbase()),
        header.getGasUsed(),
        header.getGasLimit(),
        header.getDifficulty().toUInt256(),
        header.getDifficulty().add(difficulty).toUInt256(),
        txs.stream().map(this::convertTxToStats).collect(Collectors.toList()),
        Hash.fromBytes(header.getTransactionsRoot()),
        Hash.fromBytes(header.getStateRoot()),
        ommers.stream().map(this::convertToBlockHeader).collect(Collectors.toList()));
  }

  private org.apache.tuweni.eth.BlockHeader convertToBlockHeader(final BlockHeader header) {
    return new org.apache.tuweni.eth.BlockHeader(
        Hash.fromBytes(header.getParentHash()),
        Hash.fromBytes(header.getOmmersHash()),
        Address.fromBytes(header.getCoinbase()),
        Hash.fromBytes(header.getStateRoot()),
        Hash.fromBytes(header.getTransactionsRoot()),
        Hash.fromBytes(header.getReceiptsRoot()),
        header.getLogsBloom(),
        header.getDifficulty().toUInt256(),
        UInt256.valueOf(header.getNumber()),
        Gas.valueOf(header.getGasLimit()),
        Gas.valueOf(header.getGasUsed()),
        Instant.ofEpochMilli(header.getTimestamp()),
        header.getExtraData(),
        Hash.fromBytes(header.getMixHash()),
        Bytes.ofUnsignedLong(header.getNonce()));
  }

  private TxStats convertTxToStats(final Transaction transaction) {
    return new TxStats(Hash.fromBytes(transaction.getHash()));
  }

  private void onNewBlock(final Block block, final Difficulty difficulty) {
    reporter.sendNewHead(
        convertToStats(
            block.getHeader(),
            block.getBody().getTransactions(),
            block.getBody().getOmmers(),
            difficulty));
  }

  public void start(final Vertx vertx) {
    Log4j2LoggerProvider provider = new Log4j2LoggerProvider();
    URI secureEthStatsURI =
        URI.create(
            "wss://" + ethStatsParameters.getHost() + ":" + ethStatsParameters.getPort() + "/api");
    URI ethStatsURI =
        URI.create(
            "ws://" + ethStatsParameters.getHost() + ":" + ethStatsParameters.getPort() + "/api");
    // TODO provide client identification string
    this.reporter =
        new EthStatsReporter(
            vertx,
            provider.getLogger("ethstats"),
            Arrays.asList(secureEthStatsURI, ethStatsURI),
            ethStatsParameters.getSecret(),
            "Hyperledger Besu",
            ethStatsParameters.getNode(),
            ethStatsParameters.getPort(),
            networkId.toString(),
            networkInfo,
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            this::requestHistory);
    this.blockSubscriberId = blockBroadcaster.subscribePropagateNewBlocks(this::onNewBlock);

    reportNodeStats();
    reportTxStats();
    nodeReporting = vertx.periodicStream(NODE_REPORTS_PERIOD).handler(handler -> reportNodeStats());
    txReporting = vertx.periodicStream(TX_REPORTS_PERIOD).handler(handler -> reportTxStats());

    reporter.start();
  }

  private void reportTxStats() {
    reporter.sendNewPendingTransactionCount(transactionPool.getPendingTransactions().size());
  }

  private void reportNodeStats() {
    // TODO find how to report gas price
    reporter.sendNewNodeStats(
        new NodeStats(true, syncState.isInSync(), miningEnabled, 0, ethPeers.peerCount(), 0, 100));
  }

  private void requestHistory(final List<UInt256> blockNumbers) {
    List<BlockStats> blocks =
        blockNumbers.stream()
            .map(
                number ->
                    protocolContext
                        .getBlockchain()
                        .getBlockByNumber(number.toLong())
                        .map(
                            block ->
                                convertToStats(
                                    block,
                                    protocolContext
                                        .getBlockchain()
                                        .getTotalDifficultyByHash(block.getHash())
                                        .orElse(Difficulty.ZERO)))
                        .orElse(null))
            .collect(Collectors.toList());
    reporter.sendHistoryResponse(blocks);
  }

  public void stop() {
    blockBroadcaster.unsubscribePropagateNewBlocks(blockSubscriberId);
    nodeReporting.cancel();
    txReporting.cancel();
    reporter.stop();
  }
}
