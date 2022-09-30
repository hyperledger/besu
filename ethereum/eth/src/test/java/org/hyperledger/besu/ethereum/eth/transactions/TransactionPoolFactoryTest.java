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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionPoolFactoryTest {
  @Mock ProtocolSchedule schedule;
  @Mock ProtocolContext context;
  @Mock MutableBlockchain blockchain;
  @Mock EthContext ethContext;
  @Mock EthMessages ethMessages;
  @Mock EthScheduler ethScheduler;

  @Mock GasPricePendingTransactionsSorter pendingTransactions;
  @Mock PeerTransactionTracker peerTransactionTracker;
  @Mock TransactionsMessageSender transactionsMessageSender;

  @Mock NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;

  TransactionPool pool;
  EthPeers ethPeers;

  SyncState syncState;

  EthProtocolManager ethProtocolManager;

  @Before
  public void setup() {
    when(blockchain.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(mock(Hash.class)));
    when(context.getBlockchain()).thenReturn(blockchain);
    ethPeers =
        new EthPeers(
            "ETH",
            TestClock.fixed(),
            new NoOpMetricsSystem(),
            25,
            EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE);
    when(ethContext.getEthMessages()).thenReturn(ethMessages);
    when(ethContext.getEthPeers()).thenReturn(ethPeers);

    when(ethContext.getScheduler()).thenReturn(ethScheduler);
  }

  @Test
  public void disconnectNotInvokedBeforeInitialSyncIsDone() {
    setupInitialSyncPhase(true);
    final RespondingEthPeer ethPeer =
        RespondingEthPeer.builder().ethProtocolManager(ethProtocolManager).build();
    assertThat(ethPeer.getEthPeer()).isNotNull();
    assertThat(ethPeer.getEthPeer().isDisconnected()).isFalse();
    ethPeer.disconnect(DisconnectMessage.DisconnectReason.CLIENT_QUITTING);
    verifyNoInteractions(peerTransactionTracker);
  }

  @Test
  public void disconnectInvokedAfterInitialSyncIsDone() {
    setupInitialSyncPhase(true);
    final RespondingEthPeer ethPeer =
        RespondingEthPeer.builder().ethProtocolManager(ethProtocolManager).build();
    assertThat(ethPeer.getEthPeer()).isNotNull();
    assertThat(ethPeer.getEthPeer().isDisconnected()).isFalse();

    syncState.markInitialSyncPhaseAsDone();

    ethPeer.disconnect(DisconnectMessage.DisconnectReason.CLIENT_QUITTING);
    verify(peerTransactionTracker, times(1)).onDisconnect(ethPeer.getEthPeer());
  }

  @Test
  public void disconnectInvokedIfNoInitialSync() {
    setupInitialSyncPhase(false);
    final RespondingEthPeer ethPeer =
        RespondingEthPeer.builder().ethProtocolManager(ethProtocolManager).build();
    assertThat(ethPeer.getEthPeer()).isNotNull();
    assertThat(ethPeer.getEthPeer().isDisconnected()).isFalse();

    ethPeer.disconnect(DisconnectMessage.DisconnectReason.CLIENT_QUITTING);
    verify(peerTransactionTracker, times(1)).onDisconnect(ethPeer.getEthPeer());
  }

  @Test
  public void notRegisteredToBlockAddedEventBeforeInitialSyncIsDone() {
    setupInitialSyncPhase(true);
    ArgumentCaptor<BlockAddedObserver> blockAddedListeners =
        ArgumentCaptor.forClass(BlockAddedObserver.class);
    verify(blockchain, atLeastOnce()).observeBlockAdded(blockAddedListeners.capture());

    assertThat(blockAddedListeners.getAllValues()).doesNotContain(pool);
  }

  @Test
  public void registeredToBlockAddedEventAfterInitialSyncIsDone() {
    setupInitialSyncPhase(true);
    syncState.markInitialSyncPhaseAsDone();

    ArgumentCaptor<BlockAddedObserver> blockAddedListeners =
        ArgumentCaptor.forClass(BlockAddedObserver.class);
    verify(blockchain, atLeastOnce()).observeBlockAdded(blockAddedListeners.capture());

    assertThat(blockAddedListeners.getAllValues()).contains(pool);
  }

  @Test
  public void registeredToBlockAddedEventIfNoInitialSync() {
    setupInitialSyncPhase(false);

    ArgumentCaptor<BlockAddedObserver> blockAddedListeners =
        ArgumentCaptor.forClass(BlockAddedObserver.class);
    verify(blockchain, atLeastOnce()).observeBlockAdded(blockAddedListeners.capture());

    assertThat(blockAddedListeners.getAllValues()).contains(pool);
  }

  @Test
  public void incomingTransactionMessageHandlersNotRegisteredBeforeInitialSyncIsDone() {
    setupInitialSyncPhase(true);
    ArgumentCaptor<EthMessages.MessageCallback> messageHandlers =
        ArgumentCaptor.forClass(EthMessages.MessageCallback.class);
    verify(ethMessages, atLeast(0)).subscribe(anyInt(), messageHandlers.capture());

    assertThat(messageHandlers.getAllValues())
        .doesNotHaveAnyElementsOfTypes(
            TransactionsMessageHandler.class, NewPooledTransactionHashesMessageHandler.class);
  }

  @Test
  public void incomingTransactionMessageHandlersRegisteredAfterInitialSyncIsDone() {
    setupInitialSyncPhase(true);
    syncState.markInitialSyncPhaseAsDone();

    ArgumentCaptor<EthMessages.MessageCallback> messageHandlers =
        ArgumentCaptor.forClass(EthMessages.MessageCallback.class);
    verify(ethMessages, atLeast(0)).subscribe(anyInt(), messageHandlers.capture());

    assertThat(messageHandlers.getAllValues())
        .hasAtLeastOneElementOfType(TransactionsMessageHandler.class);
    assertThat(messageHandlers.getAllValues())
        .hasAtLeastOneElementOfType(NewPooledTransactionHashesMessageHandler.class);
  }

  @Test
  public void incomingTransactionMessageHandlersRegisteredIfNoInitialSync() {
    setupInitialSyncPhase(false);

    ArgumentCaptor<EthMessages.MessageCallback> messageHandlers =
        ArgumentCaptor.forClass(EthMessages.MessageCallback.class);
    verify(ethMessages, atLeast(0)).subscribe(anyInt(), messageHandlers.capture());

    assertThat(messageHandlers.getAllValues())
        .hasAtLeastOneElementOfType(TransactionsMessageHandler.class);
    assertThat(messageHandlers.getAllValues())
        .hasAtLeastOneElementOfType(NewPooledTransactionHashesMessageHandler.class);
  }

  private void setupInitialSyncPhase(final boolean hasInitialSyncPhase) {
    syncState = new SyncState(blockchain, ethPeers, hasInitialSyncPhase, Optional.empty());

    pool =
        TransactionPoolFactory.createTransactionPool(
            schedule,
            context,
            ethContext,
            new NoOpMetricsSystem(),
            syncState,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ONE).build(),
            ImmutableTransactionPoolConfiguration.builder()
                .txPoolMaxSize(1)
                .txMessageKeepAliveSeconds(1)
                .pendingTxRetentionPeriod(1)
                .build(),
            pendingTransactions,
            peerTransactionTracker,
            transactionsMessageSender,
            newPooledTransactionHashesMessageSender);

    ethProtocolManager =
        new EthProtocolManager(
            blockchain,
            BigInteger.ONE,
            mock(WorldStateArchive.class),
            pool,
            EthProtocolConfiguration.defaultConfig(),
            ethPeers,
            mock(EthMessages.class),
            ethContext,
            Collections.emptyList(),
            Optional.empty(),
            true,
            mock(EthScheduler.class),
            mock(ForkIdManager.class));
  }
}
