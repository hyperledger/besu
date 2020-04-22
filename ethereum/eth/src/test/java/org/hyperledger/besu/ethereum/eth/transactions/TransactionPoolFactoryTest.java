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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.ForkIdManager;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;

public class TransactionPoolFactoryTest {

  @Test
  public void testDisconnect() {
    final ProtocolSchedule<?> schedule = mock(ProtocolSchedule.class);
    final ProtocolContext<?> context = mock(ProtocolContext.class);
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);

    when(blockchain.getBlockByNumber(anyLong())).thenReturn(Optional.of(mock(Block.class)));
    when(blockchain.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(mock(Hash.class)));
    when(context.getBlockchain()).thenReturn(blockchain);
    final EthPeers ethPeers = new EthPeers("ETH", TestClock.fixed(), new NoOpMetricsSystem());
    final EthContext ethContext = mock(EthContext.class);
    when(ethContext.getEthMessages()).thenReturn(mock(EthMessages.class));
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    final SyncState state = mock(SyncState.class);
    final PendingTransactions pendingTransactions = mock(PendingTransactions.class);
    final PeerTransactionTracker peerTransactionTracker = mock(PeerTransactionTracker.class);
    final TransactionsMessageSender transactionsMessageSender =
        mock(TransactionsMessageSender.class);
    final PeerPendingTransactionTracker peerPendingTransactionTracker =
        mock(PeerPendingTransactionTracker.class);
    final PendingTransactionsMessageSender pendingTransactionsMessageSender =
        mock(PendingTransactionsMessageSender.class);
    final TransactionPool pool =
        TransactionPoolFactory.createTransactionPool(
            schedule,
            context,
            ethContext,
            new NoOpMetricsSystem(),
            state,
            Wei.of(1),
            new TransactionPoolConfiguration(1, 1, 1, 1),
            pendingTransactions,
            peerTransactionTracker,
            transactionsMessageSender,
            Optional.of(peerPendingTransactionTracker),
            Optional.of(pendingTransactionsMessageSender),
            true,
            Optional.empty());

    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            blockchain,
            BigInteger.ONE,
            mock(WorldStateArchive.class),
            pool,
            new EthProtocolConfiguration(5, 5, 5, 5, 5, true),
            ethPeers,
            mock(EthMessages.class),
            ethContext,
            Collections.emptyList(),
            true,
            mock(EthScheduler.class),
            mock(ForkIdManager.class));

    final RespondingEthPeer ethPeer =
        RespondingEthPeer.builder().ethProtocolManager(ethProtocolManager).build();
    assertThat(ethPeer.getEthPeer()).isNotNull();
    assertThat(ethPeer.getEthPeer().isDisconnected()).isFalse();
    ethPeer.disconnect(DisconnectMessage.DisconnectReason.CLIENT_QUITTING);
    verify(peerTransactionTracker, times(1)).onDisconnect(ethPeer.getEthPeer());
    verify(peerPendingTransactionTracker, times(1)).onDisconnect(ethPeer.getEthPeer());
  }

  @SuppressWarnings("unchecked")
  @Test
  public <C> void testNoEth65() {
    final EthPeers ethPeers = new EthPeers("ETH", TestClock.fixed(), new NoOpMetricsSystem());

    final BlockHeader blockHeader = mock(BlockHeader.class);
    final EthContext ethContext = mock(EthContext.class);
    final EthScheduler ethScheduler = mock(EthScheduler.class);
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    final PeerTransactionTracker peerTransactionTracker = mock(PeerTransactionTracker.class);
    final PendingTransactions pendingTransactions = mock(PendingTransactions.class);
    final ProtocolContext<C> context = mock(ProtocolContext.class);
    final ProtocolSchedule<C> schedule = mock(ProtocolSchedule.class);
    final ProtocolSpec<C> protocolSpec = mock(ProtocolSpec.class);
    final SyncState state = mock(SyncState.class);
    final TransactionsMessageSender transactionsMessageSender =
        mock(TransactionsMessageSender.class);
    final TransactionValidator transactionValidator = mock(TransactionValidator.class);
    final WorldState worldState = mock(WorldState.class);
    final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);

    when(blockchain.getBlockByNumber(anyLong())).thenReturn(Optional.of(mock(Block.class)));
    when(blockchain.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(mock(Hash.class)));
    when(blockchain.getBlockHeader(any())).thenReturn(Optional.of(blockHeader));
    when(context.getBlockchain()).thenReturn(blockchain);
    when(context.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(ethContext.getEthMessages()).thenReturn(mock(EthMessages.class));
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    when(ethContext.getScheduler()).thenReturn(ethScheduler);
    when(pendingTransactions.addLocalTransaction(any())).thenReturn(true);
    when(protocolSpec.getTransactionValidator()).thenReturn(transactionValidator);
    when(schedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(transactionValidator.validate(any())).thenReturn(ValidationResult.valid());
    when(transactionValidator.validateForSender(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(worldStateArchive.get(any())).thenReturn(Optional.of(worldState));

    final TransactionPool pool =
        TransactionPoolFactory.createTransactionPool(
            schedule,
            context,
            ethContext,
            new NoOpMetricsSystem(),
            state,
            Wei.of(1),
            new TransactionPoolConfiguration(1, 1, 1, 1),
            pendingTransactions,
            peerTransactionTracker,
            transactionsMessageSender,
            Optional.empty(),
            Optional.empty(),
            false,
            Optional.empty());

    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            blockchain,
            BigInteger.ONE,
            mock(WorldStateArchive.class),
            pool,
            new EthProtocolConfiguration(5, 5, 5, 5, 5, true),
            ethPeers,
            mock(EthMessages.class),
            ethContext,
            Collections.emptyList(),
            true,
            mock(EthScheduler.class),
            mock(ForkIdManager.class));

    // Now that we have the mocks we try to run the methods and see if smoke comes out.
    // Only exceptions cause the test to fail.

    RespondingEthPeer.builder().ethProtocolManager(ethProtocolManager).build();
    final Transaction transaction =
        new TransactionTestFixture()
            .nonce(1)
            .gasLimit(0)
            .gasPrice(Wei.of(1))
            .createTransaction(KeyPair.generate());
    pool.addLocalTransaction(transaction);
  }
}
