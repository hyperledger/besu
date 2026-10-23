/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.CallParameter;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService.SimulationParameters;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.BlockAdapterBase;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.CallResult;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.NormalBlockAdapter;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.PendingStateAdapter;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The {@code call} and {@code estimateGas} fields of blocks, ommers and the pending state.
 *
 * <p>Besu's core GraphQL passed the header of the block, or of the ommer, straight to the
 * transaction simulator, which returned no result when the world state after it was unavailable.
 * Ommers are not stored as blocks, so their state is never available. Either way, a block reports
 * no {@code call} and an {@code estimateGas} of 0, and the pending state reports neither. The
 * simulation service reports an unknown block, such as an ommer, with an invalid {@code
 * BLOCK_NOT_FOUND} result that has no transaction, or with no result.
 */
@ExtendWith(MockitoExtension.class)
class CallDataFetcherTest extends AbstractDataFetcherTest {

  private static final Hash BLOCK_HASH = Hash.hash(Bytes.of(1));
  private static final Hash OMMER_HASH = Hash.hash(Bytes.of(2));
  private static final Hash HEAD_HASH = Hash.hash(Bytes.of(3));

  @Mock private TransactionSimulationService transactionSimulationService;
  @Mock private BlockHeader ommerHeader;

  @BeforeEach
  @Override
  public void before() {
    super.before();
    when(graphQLContext.get(GraphQLContextType.TRANSACTION_SIMULATION_SERVICE))
        .thenReturn(transactionSimulationService);
    when(environment.getArgument("data")).thenReturn(Map.of());
  }

  private NormalBlockAdapter blockWithOneOmmer() throws Exception {
    when(environment.getArgument("number")).thenReturn(1L);
    when(environment.getArgument("hash")).thenReturn(null);
    when(blockchainService.getBlockByNumber(1L)).thenReturn(Optional.of(blockContext));
    // lenient, as only the tests of ommers read them
    lenient()
        .when(blockchainService.getBlockByHash(BLOCK_HASH))
        .thenReturn(Optional.of(blockContext));
    when(blockchainService.getTotalDifficulty(BLOCK_HASH)).thenReturn(Optional.of(UInt256.ONE));
    when(blockContext.getBlockHeader()).thenReturn(header);
    when(blockContext.getBlockBody()).thenReturn(body);
    when(header.getBlockHash()).thenReturn(BLOCK_HASH);
    lenient().doReturn(List.of(ommerHeader)).when(body).getOmmers();
    lenient().when(ommerHeader.getBlockHash()).thenReturn(OMMER_HASH);
    return fetchers.getBlockDataFetcher().get(environment).orElseThrow();
  }

  private BlockAdapterBase ommerAt(final NormalBlockAdapter block) {
    when(environment.getArgument("index")).thenReturn(0);
    final Optional<? extends BlockAdapterBase> ommer = block.getOmmerAt(environment);
    assertThat(ommer).isPresent();
    return ommer.get();
  }

  private static TransactionSimulationResult blockNotFound() {
    // the result the simulation service gives for an unknown block: no transaction was built
    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    lenient().when(result.isInvalid()).thenReturn(true);
    lenient().when(result.getInvalidReason()).thenReturn(Optional.of("BLOCK_NOT_FOUND"));
    return new TransactionSimulationResult(null, result);
  }

  private static TransactionSimulationResult successfulResult() {
    final Transaction transaction = mock(Transaction.class);
    when(transaction.getGasLimit()).thenReturn(50_000L);
    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getGasRemaining()).thenReturn(28_000L);
    when(result.getOutput()).thenReturn(Bytes.fromHexString("0x01"));
    return new TransactionSimulationResult(transaction, result);
  }

  private void simulationAt(final Hash blockHash, final Optional<TransactionSimulationResult> r) {
    when(transactionSimulationService.simulate(
            any(CallParameter.class),
            eq(Optional.empty()),
            eq(blockHash),
            eq(OperationTracer.NO_TRACING),
            any()))
        .thenReturn(r);
  }

  @Test
  void ommerEstimateGasIsZeroWhenTheOmmerIsNotFound() throws Exception {
    final BlockAdapterBase ommer = ommerAt(blockWithOneOmmer());
    simulationAt(OMMER_HASH, Optional.of(blockNotFound()));

    assertThat(ommer.getEstimateGas(environment)).isZero();
  }

  @Test
  void ommerCallIsNullWhenTheOmmerIsNotFound() throws Exception {
    final BlockAdapterBase ommer = ommerAt(blockWithOneOmmer());
    simulationAt(OMMER_HASH, Optional.of(blockNotFound()));

    assertThat(ommer.getCall(environment)).isEmpty();
  }

  @Test
  void ommerCallAndEstimateGasWhenTheServiceGivesNoResult() throws Exception {
    final BlockAdapterBase ommer = ommerAt(blockWithOneOmmer());
    simulationAt(OMMER_HASH, Optional.empty());

    assertThat(ommer.getCall(environment)).isEmpty();
    assertThat(ommer.getEstimateGas(environment)).isZero();
  }

  @Test
  void ommersListEstimateGasIsZeroWhenTheOmmerIsNotFound() throws Exception {
    final List<? extends BlockAdapterBase> ommers = blockWithOneOmmer().getOmmers(environment);
    assertThat(ommers).hasSize(1);
    simulationAt(OMMER_HASH, Optional.of(blockNotFound()));

    assertThat(ommers.get(0).getEstimateGas(environment)).isZero();
    assertThat(ommers.get(0).getCall(environment)).isEmpty();
  }

  @Test
  void ommerCallIsSimulatedAtTheOmmer() throws Exception {
    final BlockAdapterBase ommer = ommerAt(blockWithOneOmmer());
    simulationAt(OMMER_HASH, Optional.empty());

    ommer.getCall(environment);

    verify(transactionSimulationService)
        .simulate(
            any(CallParameter.class),
            eq(Optional.empty()),
            eq(OMMER_HASH),
            eq(OperationTracer.NO_TRACING),
            eq(EnumSet.of(SimulationParameters.ALLOW_EXCEEDING_BALANCE)));
  }

  @Test
  void blockCallIsNullAndEstimateGasZeroWhenTheWorldStateIsUnavailable() throws Exception {
    final NormalBlockAdapter block = blockWithOneOmmer();
    simulationAt(BLOCK_HASH, Optional.empty());

    assertThat(block.getCall(environment)).isEmpty();
    assertThat(block.getEstimateGas(environment)).isZero();
  }

  @Test
  void blockCallIsNullAndEstimateGasZeroWhenTheBlockIsNotFound() throws Exception {
    final NormalBlockAdapter block = blockWithOneOmmer();
    simulationAt(BLOCK_HASH, Optional.of(blockNotFound()));

    assertThat(block.getCall(environment)).isEmpty();
    assertThat(block.getEstimateGas(environment)).isZero();
  }

  @Test
  void blockCallReportsTheResultOfTheSimulation() throws Exception {
    final NormalBlockAdapter block = blockWithOneOmmer();
    simulationAt(BLOCK_HASH, Optional.of(successfulResult()));

    final Optional<CallResult> call = block.getCall(environment);
    assertThat(call).isPresent();
    assertThat(call.get().getStatus()).isEqualTo(1L);
    assertThat(call.get().getGasUsed()).isEqualTo(22_000L);
    assertThat(call.get().getData()).isEqualTo(Bytes.fromHexString("0x01"));
    assertThat(block.getEstimateGas(environment)).isEqualTo(22_000L);
  }

  @Test
  void pendingCallAndEstimateGasAreNullWhenTheWorldStateIsUnavailable() {
    final PendingStateAdapter pending = new PendingStateAdapter(mock(TransactionPoolService.class));
    when(blockchainService.getChainHeadHash()).thenReturn(HEAD_HASH);
    simulationAt(HEAD_HASH, Optional.empty());

    assertThat(pending.getCall(environment)).isEmpty();
    assertThat(pending.getEstimateGas(environment)).isEmpty();
  }

  @Test
  void pendingCallAndEstimateGasAreNullWhenTheHeadIsNotFound() {
    final PendingStateAdapter pending = new PendingStateAdapter(mock(TransactionPoolService.class));
    when(blockchainService.getChainHeadHash()).thenReturn(HEAD_HASH);
    simulationAt(HEAD_HASH, Optional.of(blockNotFound()));

    assertThat(pending.getCall(environment)).isEmpty();
    assertThat(pending.getEstimateGas(environment)).isEmpty();
  }

  @Test
  void pendingCallIsSimulatedAtTheHead() {
    final PendingStateAdapter pending = new PendingStateAdapter(mock(TransactionPoolService.class));
    when(blockchainService.getChainHeadHash()).thenReturn(HEAD_HASH);
    simulationAt(HEAD_HASH, Optional.of(successfulResult()));

    assertThat(pending.getEstimateGas(environment)).contains(22_000L);
    verify(transactionSimulationService)
        .simulate(
            any(CallParameter.class),
            eq(Optional.empty()),
            eq(HEAD_HASH),
            eq(OperationTracer.NO_TRACING),
            eq(
                EnumSet.of(
                    SimulationParameters.ALLOW_EXCEEDING_BALANCE,
                    SimulationParameters.ALLOW_FUTURE_NONCE)));
  }
}
