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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethstats.request.EthStatsRequest;
import org.hyperledger.besu.ethstats.util.EthStatsConnectOptions;
import org.hyperledger.besu.ethstats.util.ImmutableEthStatsConnectOptions;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EthStatsServiceTest {

  @Mock private Vertx vertx;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private EthProtocolManager ethProtocolManager;
  @Mock private TransactionPool transactionPool;
  @Mock private MiningCoordinator miningCoordinator;
  @Mock private SyncState syncState;
  @Mock private GenesisConfigOptions genesisConfigOptions;
  @Mock private P2PNetwork p2PNetwork;
  @Mock private EthContext ethContext;
  @Mock private EthScheduler ethScheduler;
  @Mock private HttpClient httpClient;
  @Mock private WebSocket webSocket;

  final EthStatsConnectOptions ethStatsConnectOptions =
      ImmutableEthStatsConnectOptions.builder()
          .nodeName("besu-node")
          .secret("secret")
          .host("127.0.0.1")
          .port(1111)
          .contact("contact@test.net")
          .build();

  final EnodeURL node =
      EnodeURLImpl.builder()
          .nodeId(
              "50203c6bfca6874370e71aecc8958529fd723feb05013dc1abca8fc1fff845c5259faba05852e9dfe5ce172a7d6e7c2a3a5eaa8b541c8af15ea5518bbff5f2fa")
          .useDefaultPorts()
          .ipAddress("127.0.0.1")
          .listeningPort(30304)
          .build();

  private EthStatsService ethStatsService;

  @BeforeEach
  public void initMocks() {
    when(ethProtocolManager.ethContext()).thenReturn(ethContext);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    when(vertx.createHttpClient(any(HttpClientOptions.class))).thenReturn(httpClient);
    when(genesisConfigOptions.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
    when(ethProtocolManager.getSupportedCapabilities())
        .thenReturn(List.of(Capability.create("eth64", 1)));
  }

  @Test
  public void shouldRetryWhenLocalEnodeNotAvailable() throws Exception {
    ethStatsService =
        new EthStatsService(
            ethStatsConnectOptions,
            blockchainQueries,
            ethProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenThrow(new NoSuchElementException());
    ethStatsService.start();
    verify(ethScheduler, times(1)).scheduleFutureTask(any(Runnable.class), any(Duration.class));
  }

  @Test
  public void shouldSendHelloMessage() {
    ethStatsService =
        new EthStatsService(
            ethStatsConnectOptions,
            blockchainQueries,
            ethProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    final ArgumentCaptor<Handler<AsyncResult<WebSocket>>> webSocketCaptor =
        ArgumentCaptor.forClass(Handler.class);

    ethStatsService.start();

    verify(httpClient, times(1))
        .webSocket(any(WebSocketConnectOptions.class), webSocketCaptor.capture());
    webSocketCaptor.getValue().handle(succeededWebSocketEvent(Optional.of(webSocket)));

    final ArgumentCaptor<String> helloMessageCaptor = ArgumentCaptor.forClass(String.class);
    verify(webSocket, times(1)).writeTextMessage(helloMessageCaptor.capture(), any(Handler.class));

    assertThat(helloMessageCaptor.getValue().contains(EthStatsRequest.Type.HELLO.getValue()))
        .isTrue();
  }

  @Test
  public void shouldRetryIfSendHelloMessageFailed() {

    ethStatsService =
        new EthStatsService(
            ethStatsConnectOptions,
            blockchainQueries,
            ethProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    ethStatsService.start();

    final ArgumentCaptor<Handler<AsyncResult<WebSocket>>> webSocketCaptor =
        ArgumentCaptor.forClass(Handler.class);
    verify(httpClient, times(1))
        .webSocket(any(WebSocketConnectOptions.class), webSocketCaptor.capture());
    webSocketCaptor.getValue().handle(succeededWebSocketEvent(Optional.of(webSocket)));

    final ArgumentCaptor<Handler<AsyncResult<Void>>> helloMessageCaptor =
        ArgumentCaptor.forClass(Handler.class);
    verify(webSocket, times(1)).writeTextMessage(anyString(), helloMessageCaptor.capture());
    helloMessageCaptor.getValue().handle(failedWebSocketEvent(Optional.empty()));

    verify(ethScheduler, times(1)).scheduleFutureTask(any(Runnable.class), any(Duration.class));
  }

  @Test
  public void shouldSendFullReportIfHelloMessageSucceeded() {
    ethStatsService =
        new EthStatsService(
            ethStatsConnectOptions,
            blockchainQueries,
            ethProtocolManager,
            transactionPool,
            miningCoordinator,
            syncState,
            vertx,
            "clientVersion",
            genesisConfigOptions,
            p2PNetwork);
    when(p2PNetwork.getLocalEnode()).thenReturn(Optional.of(node));

    final ArgumentCaptor<Handler<AsyncResult<WebSocket>>> webSocketCaptor =
        ArgumentCaptor.forClass(Handler.class);

    ethStatsService.start();

    verify(httpClient, times(1))
        .webSocket(any(WebSocketConnectOptions.class), webSocketCaptor.capture());
    webSocketCaptor.getValue().handle(succeededWebSocketEvent(Optional.of(webSocket)));

    final ArgumentCaptor<Handler<String>> textMessageHandlerCaptor =
        ArgumentCaptor.forClass(Handler.class);
    verify(webSocket, times(1)).textMessageHandler(textMessageHandlerCaptor.capture());

    textMessageHandlerCaptor.getValue().handle("{\"emit\":[\"ready\"]}");

    verify(ethScheduler, times(1)).scheduleFutureTaskWithFixedDelay(any(), any(), any());
  }

  private <T> AsyncResult<T> succeededWebSocketEvent(final Optional<T> object) {
    return new AsyncResult<>() {
      @Override
      public T result() {
        return object.orElse(null);
      }

      @Override
      public Throwable cause() {
        return null;
      }

      @Override
      public boolean succeeded() {
        return true;
      }

      @Override
      public boolean failed() {
        return false;
      }
    };
  }

  private AsyncResult<Void> failedWebSocketEvent(final Optional<Throwable> cause) {
    return new AsyncResult<>() {
      @Override
      public Void result() {
        return null;
      }

      @Override
      public Throwable cause() {
        return cause.orElse(null);
      }

      @Override
      public boolean succeeded() {
        return false;
      }

      @Override
      public boolean failed() {
        return true;
      }
    };
  }
}
