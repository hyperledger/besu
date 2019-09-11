/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.TransactionTestFixture;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthMessages;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.eth.manager.EthScheduler;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockBroadcaster;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.data.BlockHeader;
import tech.pegasys.pantheon.plugin.data.SyncStatus;
import tech.pegasys.pantheon.plugin.data.Transaction;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PantheonEventsImplTest {

  private static final KeyPair KEY_PAIR1 = KeyPair.generate();
  private static final tech.pegasys.pantheon.ethereum.core.Transaction TX1 = createTransaction(1);
  private static final tech.pegasys.pantheon.ethereum.core.Transaction TX2 = createTransaction(2);

  @Mock private ProtocolSchedule<Void> mockProtocolSchedule;
  @Mock private ProtocolContext<Void> mockProtocolContext;
  private SyncState syncState;
  @Mock private EthPeers mockEthPeers;
  @Mock private EthContext mockEthContext;
  @Mock private EthMessages mockEthMessages;
  @Mock private EthScheduler mockEthScheduler;
  @Mock private MutableBlockchain mockBlockchain;
  @Mock private TransactionValidator mockTransactionValidator;
  @Mock private ProtocolSpec<Void> mockProtocolSpec;
  @Mock private WorldStateArchive mockWorldStateArchive;
  @Mock private WorldState mockWorldState;
  private tech.pegasys.pantheon.ethereum.core.BlockHeader fakeBlockHeader;
  private TransactionPool transactionPool;
  private BlockBroadcaster blockBroadcaster;
  private PantheonEventsImpl serviceImpl;

  @Before
  public void setUp() {
    fakeBlockHeader =
        new tech.pegasys.pantheon.ethereum.core.BlockHeader(
            null, null, null, null, null, null, null, null, 1, 1, 1, 1, null, null, 1, null);

    when(mockBlockchain.getBlockHeader(any())).thenReturn(Optional.of(fakeBlockHeader));
    when(mockEthContext.getEthMessages()).thenReturn(mockEthMessages);
    when(mockEthContext.getEthPeers()).thenReturn(mockEthPeers);
    when(mockEthContext.getScheduler()).thenReturn(mockEthScheduler);
    when(mockEthPeers.streamAvailablePeers()).thenReturn(Stream.empty()).thenReturn(Stream.empty());
    when(mockProtocolContext.getBlockchain()).thenReturn(mockBlockchain);
    when(mockProtocolContext.getWorldStateArchive()).thenReturn(mockWorldStateArchive);
    when(mockProtocolSchedule.getByBlockNumber(anyLong())).thenReturn(mockProtocolSpec);
    when(mockProtocolSpec.getTransactionValidator()).thenReturn(mockTransactionValidator);
    when(mockTransactionValidator.validate(any())).thenReturn(ValidationResult.valid());
    when(mockTransactionValidator.validateForSender(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(mockWorldStateArchive.get(any())).thenReturn(Optional.of(mockWorldState));

    blockBroadcaster = new BlockBroadcaster(mockEthContext);
    transactionPool =
        TransactionPoolFactory.createTransactionPool(
            mockProtocolSchedule,
            mockProtocolContext,
            mockEthContext,
            TestClock.fixed(),
            new NoOpMetricsSystem(),
            syncState,
            Wei.ZERO,
            TransactionPoolConfiguration.builder().txPoolMaxSize(1).build());
    syncState = new SyncState(mockBlockchain, mockEthPeers);

    serviceImpl = new PantheonEventsImpl(blockBroadcaster, transactionPool, syncState);
  }

  @Test
  public void syncStatusEventFiresAfterSubscribe() {
    final AtomicReference<SyncStatus> result = new AtomicReference<>();
    serviceImpl.addSyncStatusListener(result::set);

    assertThat(result.get()).isNull();
    syncState.publishSyncStatus();
    assertThat(result.get()).isNotNull();
  }

  @Test
  public void syncStatusEventDoesNotFireAfterUnsubscribe() {
    final AtomicReference<SyncStatus> result = new AtomicReference<>();
    final long id = serviceImpl.addSyncStatusListener(result::set);
    syncState.publishSyncStatus();
    assertThat(result.get()).isNotNull();
    result.set(null);
    serviceImpl.removeSyncStatusListener(id);
    syncState.publishSyncStatus();
    assertThat(result.get()).isNull();
  }

  @Test
  public void newBlockEventFiresAfterSubscribe() {
    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    serviceImpl.addBlockPropagatedListener(result::set);

    assertThat(result.get()).isNull();
    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));

    assertThat(result.get()).isNotNull();
  }

  @Test
  public void newBlockEventDoesNotFireAfterUnsubscribe() {
    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    final long id = serviceImpl.addBlockPropagatedListener(result::set);

    assertThat(result.get()).isNull();
    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));

    assertThat(result.get()).isNotNull();
    serviceImpl.removeBlockPropagatedListener(id);
    result.set(null);

    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));
    assertThat(result.get()).isNull();
  }

  @Test
  public void propagationWithoutSubscriptionsCompletes() {
    blockBroadcaster.propagate(generateBlock(), UInt256.of(1));
  }

  @Test
  public void newBlockEventUselessUnsubscribesCompletes() {
    serviceImpl.removeBlockPropagatedListener(5);
    serviceImpl.removeBlockPropagatedListener(5L);
  }

  @Test
  public void transactionAddedEventFiresAfterSubscribe() {
    final AtomicReference<Transaction> result = new AtomicReference<>();
    serviceImpl.addTransactionAddedListener(result::set);

    assertThat(result.get()).isNull();
    transactionPool.addLocalTransaction(TX1);

    assertThat(result.get()).isNotNull();
  }

  @Test
  public void transactionAddedEventDoesNotFireAfterUnsubscribe() {
    final AtomicReference<Transaction> result = new AtomicReference<>();
    final long id = serviceImpl.addTransactionAddedListener(result::set);

    assertThat(result.get()).isNull();
    transactionPool.addLocalTransaction(TX1);
    assertThat(result.get()).isNotNull();

    serviceImpl.removeTransactionAddedListener(id);
    result.set(null);

    transactionPool.addLocalTransaction(TX2);
    assertThat(result.get()).isNull();
  }

  @Test
  public void transactionAddedEventUselessUnsubscribesCompletes() {
    serviceImpl.removeTransactionAddedListener(5);
    serviceImpl.removeTransactionAddedListener(5L);
  }

  @Test
  public void transactionDroppedEventFiresAfterSubscribe() {
    final AtomicReference<Transaction> result = new AtomicReference<>();
    serviceImpl.addTransactionDroppedListener(result::set);

    assertThat(result.get()).isNull();
    // The max pool size is configured to 1 so adding two transactions should trigger a drop
    transactionPool.addLocalTransaction(TX1);
    transactionPool.addLocalTransaction(TX2);

    assertThat(result.get()).isNotNull();
  }

  @Test
  public void transactionDroppedEventDoesNotFireAfterUnsubscribe() {
    final AtomicReference<Transaction> result = new AtomicReference<>();
    final long id = serviceImpl.addTransactionDroppedListener(result::set);

    assertThat(result.get()).isNull();
    transactionPool.addLocalTransaction(TX1);
    transactionPool.addLocalTransaction(TX2);

    assertThat(result.get()).isNotNull();
    serviceImpl.removeTransactionAddedListener(id);
    result.set(null);

    transactionPool.addLocalTransaction(TX2);
    assertThat(result.get()).isNull();
  }

  private Block generateBlock() {
    final BlockBody body = new BlockBody(Collections.emptyList(), Collections.emptyList());
    return new Block(new BlockHeaderTestFixture().buildHeader(), body);
  }

  private static tech.pegasys.pantheon.ethereum.core.Transaction createTransaction(
      final int transactionNumber) {
    return new TransactionTestFixture()
        .nonce(transactionNumber)
        .gasLimit(0)
        .createTransaction(KEY_PAIR1);
  }
}
