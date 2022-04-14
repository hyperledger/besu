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
package org.hyperledger.besu.ethstats;

import static com.google.common.collect.Streams.stream;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.EMIT_FIELD;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.MAPPER;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.BLOCK;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.HELLO;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.HISTORY;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.LATENCY;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.NODE_PING;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.NODE_PONG;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.PENDING;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.READY;
import static org.hyperledger.besu.ethstats.request.EthStatsRequest.Type.STATS;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueMiningCoordinator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethstats.authentication.ImmutableAuthenticationData;
import org.hyperledger.besu.ethstats.authentication.ImmutableNodeInfo;
import org.hyperledger.besu.ethstats.authentication.NodeInfo;
import org.hyperledger.besu.ethstats.report.ImmutableBlockReport;
import org.hyperledger.besu.ethstats.report.ImmutableHistoryReport;
import org.hyperledger.besu.ethstats.report.ImmutableLatencyReport;
import org.hyperledger.besu.ethstats.report.ImmutableNodeStatsReport;
import org.hyperledger.besu.ethstats.report.ImmutablePendingTransactionsReport;
import org.hyperledger.besu.ethstats.report.ImmutablePingReport;
import org.hyperledger.besu.ethstats.report.NodeStatsReport;
import org.hyperledger.besu.ethstats.report.PendingTransactionsReport;
import org.hyperledger.besu.ethstats.request.EthStatsRequest;
import org.hyperledger.besu.ethstats.util.NetstatsUrl;
import org.hyperledger.besu.ethstats.util.PrimusHeartBeatsHelper;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.util.platform.PlatformDetector;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.net.ssl.SSLHandshakeException;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class describes the behaviour of the EthStats service. This class is used to report pending
 * transactions, blocks, and several node-related information to a netstats server.
 */
public class EthStatsService {

  private static final Logger LOG = LoggerFactory.getLogger(EthStatsService.class);

  private static final Duration SEND_REPORT_DELAY = Duration.ofSeconds(5);
  private static final int HISTORY_RANGE = 50;

  private final AtomicBoolean retryInProgress = new AtomicBoolean(false);

  private final NetstatsUrl netstatsUrl;
  private final EthProtocolManager protocolManager;
  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final SyncState syncState;
  private final Vertx vertx;
  private final String clientVersion;
  private final GenesisConfigOptions genesisConfigOptions;
  private final P2PNetwork p2PNetwork;
  private final BlockchainQueries blockchainQueries;
  private final BlockResultFactory blockResultFactory;
  private final HttpClientOptions httpClientOptions;
  private final WebSocketConnectOptions webSocketConnectOptions;

  private ScheduledFuture<?> reportScheduler;
  private WebSocket webSocket;
  private EnodeURL enodeURL;
  private long pingTimestamp;

  public EthStatsService(
      final NetstatsUrl netstatsUrl,
      final BlockchainQueries blockchainQueries,
      final EthProtocolManager protocolManager,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final SyncState syncState,
      final Vertx vertx,
      final String clientVersion,
      final GenesisConfigOptions genesisConfigOptions,
      final P2PNetwork p2PNetwork) {
    this.netstatsUrl = netstatsUrl;
    this.blockchainQueries = blockchainQueries;
    this.protocolManager = protocolManager;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.vertx = vertx;
    this.syncState = syncState;
    this.clientVersion = clientVersion;
    this.genesisConfigOptions = genesisConfigOptions;
    this.p2PNetwork = p2PNetwork;
    this.blockResultFactory = new BlockResultFactory();
    this.httpClientOptions = new HttpClientOptions();
    this.webSocketConnectOptions =
        new WebSocketConnectOptions()
            .setURI("/api")
            .setHost(netstatsUrl.getHost())
            .setPort(netstatsUrl.getPort())
            .setSsl(true);
  }

  public void start() {

    try {

      enodeURL = p2PNetwork.getLocalEnode().orElseThrow();

      vertx
          .createHttpClient(httpClientOptions)
          .webSocket(
              webSocketConnectOptions,
              event -> {
                if (event.succeeded()) {
                  webSocket = event.result();

                  // reconnect if we lose the connection or if an error occurs
                  webSocket.exceptionHandler(ex -> retryConnect());
                  webSocket.closeHandler(handler -> retryConnect());

                  // listen to the messages from the ethstats server in order to validate the
                  // connection
                  webSocket.textMessageHandler(
                      ack -> {
                        EthStatsRequest ethStatsRequest = EthStatsRequest.fromResponse(ack);
                        if (ethStatsRequest.getType().equals(READY)) {
                          LOG.info("Connected to ethstats server");

                          // listen to messages from the ethstats server
                          startListeningEthstatsServer();
                          // send a full report after the connection
                          sendFullReport();
                        } else {
                          LOG.error("Failed to login to ethstats server {}", ack);
                        }
                      });

                  retryInProgress.set(false);
                  // sending a hello to initiate the connection using the secret
                  sendHello();
                } else {
                  String errorMessage =
                      "Failed to reach the ethstats server " + event.cause().getMessage();
                  if (event.cause() instanceof SSLHandshakeException) {
                    webSocketConnectOptions.setSsl(false);
                    errorMessage += " (trying without ssl)";
                  }
                  LOG.error(errorMessage);
                  retryInProgress.set(false);
                  retryConnect();
                }
              });

    } catch (Exception e) {
      retryConnect();
    }
  }

  /** Ends the current web socket connection, observers and schedulers */
  public void stop() {
    if (webSocket != null && !webSocket.isClosed()) {
      webSocket.close();
    }
    if (reportScheduler != null) {
      reportScheduler.cancel(true);
    }
  }

  /** Ends the current connection and restart a new one. */
  private void retryConnect() {
    if (retryInProgress.getAndSet(true) == FALSE) {
      stop();
      protocolManager
          .ethContext()
          .getScheduler()
          .scheduleFutureTask(this::start, Duration.ofSeconds(10));
    }
  }

  /** Sends a hello request to the ethstats server in order to log in. */
  private void sendHello() {
    try {
      final Optional<Integer> port = enodeURL.getListeningPort();
      final Optional<BigInteger> chainId = genesisConfigOptions.getChainId();
      if (port.isPresent() && chainId.isPresent()) {
        final String os = PlatformDetector.getOSType();
        final String arch = PlatformDetector.getArch();

        final NodeInfo nodeInfo =
            ImmutableNodeInfo.of(
                netstatsUrl.getNodeName(),
                clientVersion,
                String.valueOf(port.get()),
                chainId.get().toString(),
                protocolManager.getSupportedCapabilities().toString(),
                "No",
                os,
                arch,
                "0.1.1",
                true,
                netstatsUrl.getContact());

        final EthStatsRequest hello =
            new EthStatsRequest(
                HELLO,
                ImmutableAuthenticationData.of(
                    enodeURL.getNodeId().toHexString(), nodeInfo, netstatsUrl.getSecret()));
        sendMessage(
            webSocket,
            hello,
            isSucceeded -> {
              if (!isSucceeded) {
                retryConnect();
              }
            });
      } else {
        throw new NoSuchElementException();
      }
    } catch (NoSuchElementException e) {
      LOG.error("Failed to find required parameters for ethstats request : {}", e.getMessage());
      retryConnect();
    }
  }

  /** Sends a full report to the ethstats server */
  private void sendFullReport() {
    reportScheduler =
        protocolManager
            .ethContext()
            .getScheduler()
            .scheduleFutureTaskWithFixedDelay(
                () -> {
                  sendPing();
                  sendBlockReport();
                  sendPendingTransactionReport();
                  sendNodeStatsReport();
                },
                Duration.ofSeconds(0),
                SEND_REPORT_DELAY);
  }

  /** Sends a ping request to the ethstats server */
  private void sendPing() {
    // we store the timestamp when we sent the ping
    pingTimestamp = System.currentTimeMillis();

    sendMessage(
        webSocket,
        new EthStatsRequest(
            NODE_PING,
            ImmutablePingReport.of(
                enodeURL.getNodeId().toHexString(), String.valueOf(pingTimestamp))));
  }

  /** Sends a latency report to the ethstats server */
  private void sendLatencyReport() {
    sendMessage(
        webSocket,
        new EthStatsRequest(
            LATENCY,
            ImmutableLatencyReport.of(
                enodeURL.getNodeId().toHexString(),
                String.valueOf(System.currentTimeMillis() - pingTimestamp))));
  }

  /** Sends a block report concerning the last block */
  private void sendBlockReport() {
    blockchainQueries
        .latestBlock()
        .map(tx -> blockResultFactory.transactionComplete(tx, false))
        .ifPresent(
            blockResult ->
                sendMessage(
                    webSocket,
                    new EthStatsRequest(
                        BLOCK,
                        ImmutableBlockReport.of(enodeURL.getNodeId().toHexString(), blockResult))));
  }

  /** Sends a report concerning a set of blocks (range, list of blocks) */
  private void sendHistoryReport(final List<Long> blocks) {
    final List<BlockResult> blockResults = new ArrayList<>();

    blocks.forEach(
        blockNumber ->
            blockchainQueries
                .blockByNumber(blockNumber)
                .map(tx -> blockResultFactory.transactionComplete(tx, false))
                .ifPresent(blockResults::add));

    if (!blockResults.isEmpty()) {
      sendMessage(
          webSocket,
          new EthStatsRequest(
              HISTORY,
              ImmutableHistoryReport.of(enodeURL.getNodeId().toHexString(), blockResults)));
    }
  }

  /** Sends the number of pending transactions in the pool */
  private void sendPendingTransactionReport() {
    final int pendingTransactionsNumber = transactionPool.getPendingTransactions().size();

    final PendingTransactionsReport pendingTransactionsReport =
        ImmutablePendingTransactionsReport.builder()
            .id(enodeURL.getNodeId().toHexString())
            .stats(pendingTransactionsNumber)
            .build();

    sendMessage(webSocket, new EthStatsRequest(PENDING, pendingTransactionsReport));
  }

  /** Sends information about the node (is mining, is syncing, etc.) */
  private void sendNodeStatsReport() {
    final boolean isMiningEnabled;
    if (miningCoordinator instanceof CliqueMiningCoordinator) {
      isMiningEnabled = ((CliqueMiningCoordinator) miningCoordinator).isSigner();
    } else {
      isMiningEnabled = miningCoordinator.isMining();
    }
    final boolean isSyncing = syncState.isInSync();
    final long gasPrice = suggestGasPrice(blockchainQueries.getBlockchain().getChainHeadBlock());
    final long hashrate = miningCoordinator.hashesPerSecond().orElse(0L);
    final int peersNumber = protocolManager.ethContext().getEthPeers().peerCount();

    final NodeStatsReport nodeStatsReport =
        ImmutableNodeStatsReport.builder()
            .id(enodeURL.getNodeId().toHexString())
            .stats(true, isMiningEnabled, hashrate, peersNumber, gasPrice, isSyncing, 100)
            .build();
    sendMessage(webSocket, new EthStatsRequest(STATS, nodeStatsReport));
  }

  private void sendMessage(
      final WebSocket webSocket,
      final EthStatsRequest message,
      final Consumer<Boolean> handlerResult) {
    try {
      LOG.debug("Send ethstats request {}", message.generateCommand());
      webSocket.writeTextMessage(
          message.generateCommand(),
          handler -> {
            if (!handler.succeeded()) {
              LOG.error("Failed to send {} ethstats request", message.getType());
              handlerResult.accept(FALSE);
            } else {
              handlerResult.accept(TRUE);
            }
          });
    } catch (Exception e) {
      LOG.error(
          "Failed to send {} ethstats request with error {}", message.getType(), e.getMessage());
      handlerResult.accept(FALSE);
    }
  }

  private void sendMessage(final WebSocket webSocket, final EthStatsRequest message) {
    sendMessage(webSocket, message, __ -> {});
  }

  private void startListeningEthstatsServer() {

    webSocket.textMessageHandler(
        message -> {
          try {
            if (PrimusHeartBeatsHelper.isHeartBeatsRequest(message)) {
              PrimusHeartBeatsHelper.sendHeartBeatsResponse(webSocket);
            } else {
              final JsonNode jsonNode = MAPPER.readTree(message);
              final JsonNode parameters = jsonNode.get(EMIT_FIELD);
              if (parameters.isArray()) {
                final EthStatsRequest.Type type =
                    EthStatsRequest.Type.fromValue(parameters.get(0).asText());
                if (type.equals(NODE_PONG)) {
                  sendLatencyReport();

                } else if (type.equals(HISTORY)) {
                  List<Long> list =
                      stream(parameters.get(1).withArray("list").elements())
                          .map(JsonNode::asLong)
                          .collect(Collectors.toList());
                  //  if the server does not send a list, we recover the last 50 blocks
                  if (list.isEmpty()) {
                    final long chainHeadBlockNumber =
                        blockchainQueries.getBlockchain().getChainHeadBlockNumber();
                    final long startHistoryBlockNumber =
                        Math.max(0, chainHeadBlockNumber - HISTORY_RANGE);
                    list =
                        LongStream.range(chainHeadBlockNumber, startHistoryBlockNumber)
                            .boxed()
                            .collect(Collectors.toList());
                  }
                  sendHistoryReport(list);
                }
              }
            }
          } catch (Exception e) {
            LOG.debug("Ignore invalid request {}", message);
          }
        });
  }

  private long suggestGasPrice(final Block block) {
    // retrieves transactions from the last blocks and takes the lowest gas price. If no transaction
    // is present we return the minTransactionGasPrice of the mining coordinator
    return block.getBody().getTransactions().stream()
        .min(Comparator.comparing(t -> t.getEffectiveGasPrice(block.getHeader().getBaseFee())))
        .map(t -> t.getEffectiveGasPrice(block.getHeader().getBaseFee()))
        .filter(wei -> wei.getAsBigInteger().longValue() > 0)
        .orElse(miningCoordinator.getMinTransactionGasPrice())
        .getAsBigInteger()
        .longValue();
  }
}
