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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.ImmutableCallParameter;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugins.graphql.TestChainServices;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code call} and {@code estimateGas} on ommers, over the real simulation service, compared with
 * what Besu's core GraphQL returned: it passed the ommer header to {@code
 * TransactionSimulator.process}, which gives no result when the world state after the ommer is
 * unavailable, so {@code estimateGas} was 0 and {@code call} was null. An ommer that was never
 * imported is not stored, and the node never computed its state.
 */
class UncleBlockAdapterCallTest {

  private static final Map<DataStorageFormat, TestChainServices> CHAINS =
      new EnumMap<>(DataStorageFormat.class);

  @AfterAll
  static void clearChains() {
    CHAINS.clear();
  }

  private static TestChainServices chain(final DataStorageFormat format) {
    return CHAINS.computeIfAbsent(format, TestChainServices::forHiveTesting);
  }

  private static DataFetchingEnvironment environment(final TestChainServices chain) {
    final DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
    when(environment.getGraphQlContext()).thenReturn(GraphQLContext.of(chain.graphQLContext()));
    when(environment.getArgument("data")).thenReturn(Map.of());
    return environment;
  }

  /** What core GraphQL reported for {@code estimateGas}, from the same simulator. */
  private static long coreEstimateGas(final TestChainServices chain, final BlockHeader header) {
    return coreCall(chain, header).orElse(0L);
  }

  /** The gas used of the {@code call} core GraphQL reported, empty for a null {@code call}. */
  private static Optional<Long> coreCall(final TestChainServices chain, final BlockHeader header) {
    return chain
        .transactionSimulator()
        .process(
            ImmutableCallParameter.builder().build(),
            TransactionValidationParams.transactionSimulatorAllowExceedingBalance(),
            OperationTracer.NO_TRACING,
            (mutableWorldState, result) -> result.map(r -> r.getGasEstimate()),
            header);
  }

  private static BlockHeader canonicalHeader(final TestChainServices chain, final long number) {
    return chain.blockchainSetupUtil().getBlockchain().getBlockHeader(number).orElseThrow();
  }

  /** An ommer of a canonical block that the node never imported. */
  private static BlockHeader unimportedOmmer(final TestChainServices chain) {
    return BlockHeaderBuilder.fromHeader(canonicalHeader(chain, 5))
        .stateRoot(Hash.hash(Bytes.of(0x0e)))
        .extraData(Bytes.of(0x0e))
        .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
        .buildBlockHeader();
  }

  @ParameterizedTest
  @EnumSource(
      value = DataStorageFormat.class,
      names = {"BONSAI", "FOREST"})
  void unimportedOmmerHasNoCallAndZeroEstimateGas(final DataStorageFormat format) {
    final TestChainServices chain = chain(format);
    final BlockHeader ommer = unimportedOmmer(chain);
    assertThat(chain.blockchainSetupUtil().getBlockchain().getBlockHeader(ommer.getHash()))
        .isEmpty();
    final UncleBlockAdapter adapter = new UncleBlockAdapter(ommer);
    final DataFetchingEnvironment environment = environment(chain);

    assertThat(coreCall(chain, ommer)).isEmpty();
    assertThat(adapter.getCall(environment)).isEmpty();
    assertThat(adapter.getEstimateGas(environment))
        .isEqualTo(coreEstimateGas(chain, ommer))
        .isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = DataStorageFormat.class,
      names = {"BONSAI", "FOREST"})
  void storedBlockHeaderIsSimulatedAsBefore(final DataStorageFormat format) {
    final TestChainServices chain = chain(format);
    final BlockHeader header = canonicalHeader(chain, 5);
    final UncleBlockAdapter adapter = new UncleBlockAdapter(header);
    final DataFetchingEnvironment environment = environment(chain);

    final long coreEstimate = coreEstimateGas(chain, header);
    assertThat(coreEstimate).isPositive();
    assertThat(adapter.getEstimateGas(environment)).isEqualTo(coreEstimate);
    assertThat(adapter.getCall(environment).map(CallResult::getGasUsed)).contains(coreEstimate);
  }
}
