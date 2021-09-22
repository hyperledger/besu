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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ImmutableTransactionTraceParams;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionTracerTest {

  @Rule public TemporaryFolder traceDir = new TemporaryFolder();

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private Blockchain blockchain;

  @Mock private WorldStateArchive worldStateArchive;

  @Mock private BlockHeader blockHeader;

  @Mock private BlockBody blockBody;

  @Mock private BlockHeader previousBlockHeader;

  @Mock private Transaction transaction;

  @Mock private Transaction otherTransaction;

  @Mock private DebugOperationTracer tracer;

  @Mock private ProtocolSpec protocolSpec;

  @Mock private MutableWorldState mutableWorldState;

  @Mock private MainnetTransactionProcessor transactionProcessor;

  private TransactionTracer transactionTracer;

  private final Hash transactionHash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  private final Hash otherTransactionHash =
      Hash.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  private final Hash blockHash =
      Hash.fromHexString("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

  private final Hash previousBlockHash =
      Hash.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  private final Hash invalidBlockHash =
      Hash.fromHexString("1111111111111111111111111111111111111111111111111111111111111111");

  @Before
  public void setUp() throws Exception {
    transactionTracer =
        new TransactionTracer(new BlockReplay(protocolSchedule, blockchain, worldStateArchive));
    when(transaction.getHash()).thenReturn(transactionHash);
    when(otherTransaction.getHash()).thenReturn(otherTransactionHash);
    when(blockHeader.getNumber()).thenReturn(12L);
    when(blockHeader.getHash()).thenReturn(blockHash);
    when(blockHeader.getParentHash()).thenReturn(previousBlockHash);
    when(previousBlockHeader.getStateRoot()).thenReturn(Hash.ZERO);
    when(worldStateArchive.getMutable(Hash.ZERO, null, false))
        .thenReturn(Optional.of(mutableWorldState));
    when(protocolSchedule.getByBlockNumber(12)).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);
    when(blockchain.getChainHeadHeader()).thenReturn(blockHeader);
    when(protocolSpec.getBadBlocksManager()).thenReturn(new BadBlockManager());
  }

  @Test
  public void traceTransactionShouldReturnNoneWhenBlockHeaderNotFound() {
    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(invalidBlockHash, transactionHash, tracer);
    assertThat(transactionTrace).isEmpty();
  }

  @Test
  public void traceTransactionShouldReturnTraceFramesFromExecutionTracer() {
    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));
    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    final List<TraceFrame> traceFrames = Collections.singletonList(mock(TraceFrame.class));
    when(tracer.getTraceFrames()).thenReturn(traceFrames);

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertThat(transactionTrace.map(TransactionTrace::getTraceFrames)).contains(traceFrames);
  }

  @Test
  public void
      traceTransactionShouldReturnTraceFramesFromExecutionTracerAfterExecutingOtherTransactions() {
    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Arrays.asList(otherTransaction, transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    final List<TraceFrame> traceFrames = Collections.singletonList(mock(TraceFrame.class));
    when(tracer.getTraceFrames()).thenReturn(traceFrames);

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertThat(transactionTrace.map(TransactionTrace::getTraceFrames)).contains(traceFrames);
  }

  @Test
  public void traceTransactionShouldReturnResultFromProcessTransaction() {
    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final WorldUpdater updater = mutableWorldState.updater();
    final Address coinbase = blockHeader.getCoinbase();
    when(transactionProcessor.processTransaction(
            eq(blockchain),
            eq(updater),
            eq(blockHeader),
            eq(transaction),
            eq(coinbase),
            eq(tracer),
            any(),
            any(),
            any()))
        .thenReturn(result);

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertThat(transactionTrace.map(TransactionTrace::getResult)).contains(result);
  }

  @Test
  public void traceTransactionShouldReturnEmptyResultWhenTransactionNotInCurrentBlock() {

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(otherTransaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertThat(transactionTrace).isEmpty();
  }

  @Test
  public void traceTransactionShouldReturnEmptyResultWhenBlockIsNotAvailable() {

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.empty());

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertThat(transactionTrace).isEmpty();
  }

  @Test
  public void traceTransactionToFileShouldReturnEmptyListWhenNoTransaction() {

    final List<Transaction> transactions = new ArrayList<>();

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(transactions);
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final WorldUpdater updater = mock(WorldUpdater.class);
    when(mutableWorldState.updater()).thenReturn(updater);
    final List<String> transactionTraces =
        transactionTracer.traceTransactionToFile(
            blockHash,
            Optional.of(ImmutableTransactionTraceParams.builder().build()),
            traceDir.getRoot().toPath());

    assertThat(transactionTraces).isEmpty();
  }

  @Test
  public void traceTransactionToFileShouldReturnResultFromProcessTransaction() throws IOException {

    List<Transaction> transactions = Collections.singletonList(transaction);
    when(blockBody.getTransactions()).thenReturn(transactions);
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.getOutput()).thenReturn(Bytes.of(0x01, 0x02));

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final WorldUpdater updater = mock(WorldUpdater.class);
    when(mutableWorldState.updater()).thenReturn(updater);
    final WorldUpdater stackedUpdater = mock(StackedUpdater.class);
    when(updater.updater()).thenReturn(stackedUpdater);
    final Address coinbase = blockHeader.getCoinbase();
    when(transactionProcessor.processTransaction(
            eq(blockchain),
            eq(stackedUpdater),
            eq(blockHeader),
            eq(transaction),
            eq(coinbase),
            any(StandardJsonTracer.class),
            any(),
            any(),
            any()))
        .thenReturn(result);

    final List<String> transactionTraces =
        transactionTracer.traceTransactionToFile(
            blockHash,
            Optional.of(ImmutableTransactionTraceParams.builder().build()),
            traceDir.getRoot().toPath());

    assertThat(transactionTraces.size()).isEqualTo(1);
    assertThat(Files.readString(Path.of(transactionTraces.get(0))))
        .contains("{\"output\":\"0102\",\"gasUsed\":\"0x0\"");
  }
}
