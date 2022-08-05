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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

@SuppressWarnings("unchecked")
public class TransactionPoolFactoryTest {

  @Test
  public void testDisconnect() {
    final ProtocolSchedule schedule = mock(ProtocolSchedule.class);
    final ProtocolContext context = mock(ProtocolContext.class);
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);

    when(blockchain.getBlockByNumber(anyLong())).thenReturn(Optional.of(mock(Block.class)));
    when(blockchain.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(mock(Hash.class)));
    when(context.getBlockchain()).thenReturn(blockchain);
    final EthPeers ethPeers =
        new EthPeers(
            "ETH",
            TestClock.fixed(),
            new NoOpMetricsSystem(),
            25,
            EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE);
    final EthContext ethContext = mock(EthContext.class);
    when(ethContext.getEthMessages()).thenReturn(mock(EthMessages.class));
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    final EthScheduler ethScheduler = mock(EthScheduler.class);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    final GasPricePendingTransactionsSorter pendingTransactions =
        mock(GasPricePendingTransactionsSorter.class);
    final PeerTransactionTracker peerTransactionTracker = mock(PeerTransactionTracker.class);
    final TransactionsMessageSender transactionsMessageSender =
        mock(TransactionsMessageSender.class);
    doNothing().when(transactionsMessageSender).sendTransactionsToPeer(any(EthPeer.class));
    final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender =
        mock(NewPooledTransactionHashesMessageSender.class);
    final TransactionPool pool =
        TransactionPoolFactory.createTransactionPool(
            schedule,
            context,
            ethContext,
            new NoOpMetricsSystem(),
            () -> true,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ONE).build(),
            ImmutableTransactionPoolConfiguration.of(
                1,
                1,
                1,
                TransactionPoolConfiguration.DEFAULT_PRICE_BUMP,
                TransactionPoolConfiguration.ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD,
                TransactionPoolConfiguration.DEFAULT_RPC_TX_FEE_CAP,
                TransactionPoolConfiguration.DEFAULT_STRICT_TX_REPLAY_PROTECTION_ENABLED),
            pendingTransactions,
            peerTransactionTracker,
            transactionsMessageSender,
            newPooledTransactionHashesMessageSender);

    final EthProtocolManager ethProtocolManager =
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

    final RespondingEthPeer ethPeer =
        RespondingEthPeer.builder().ethProtocolManager(ethProtocolManager).build();
    assertThat(ethPeer.getEthPeer()).isNotNull();
    assertThat(ethPeer.getEthPeer().isDisconnected()).isFalse();
    ethPeer.disconnect(DisconnectMessage.DisconnectReason.CLIENT_QUITTING);
    verify(peerTransactionTracker, times(1)).onDisconnect(ethPeer.getEthPeer());
  }
}
