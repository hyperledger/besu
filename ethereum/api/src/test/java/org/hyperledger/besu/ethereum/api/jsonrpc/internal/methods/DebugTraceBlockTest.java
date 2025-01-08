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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidatorFactory;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsProcessor;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DebugTraceBlockTest {
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private Blockchain blockchain;
  @Mock private ObservableMetricsSystem metricsSystem;
  @Mock private WithdrawalsProcessor withdrawalsProcessor;
  @Mock private TransactionValidatorFactory alwaysValidTransactionValidatorFactory;
  private DebugTraceBlock debugTraceBlock;

  @BeforeEach
  public void setUp() {
    // As we build the block from RLP in DebugTraceBlock, we need to have non mocked
    // protocolSchedule (and ProtocolSpec)
    // to be able to get the hash of the block
    final var genesisConfig =
        GenesisConfig.fromResource(
            "/org/hyperledger/besu/ethereum/api/jsonrpc/trace/chain-data/genesis.json");
    final ProtocolSpecAdapters protocolSpecAdapters =
        ProtocolSpecAdapters.create(
            0,
            specBuilder -> {
              specBuilder.isReplayProtectionSupported(true);
              specBuilder.withdrawalsProcessor(withdrawalsProcessor);
              specBuilder.transactionValidatorFactoryBuilder(
                  (evm, gasLimitCalculator, feeMarket) -> alwaysValidTransactionValidatorFactory);
              return specBuilder;
            });
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder(genesisConfig)
            .protocolSchedule(
                new ProtocolScheduleBuilder(
                        genesisConfig.getConfigOptions(),
                        Optional.of(BigInteger.valueOf(42)),
                        protocolSpecAdapters,
                        PrivacyParameters.DEFAULT,
                        false,
                        EvmConfiguration.DEFAULT,
                        MiningConfiguration.MINING_DISABLED,
                        new BadBlockManager(),
                        false,
                        new NoOpMetricsSystem())
                    .createProtocolSchedule())
            .build();
    debugTraceBlock =
        new DebugTraceBlock(
            executionContextTestFixture.getProtocolSchedule(),
            blockchainQueries,
            metricsSystem,
            new DeterministicEthScheduler());
  }

  @Test
  public void nameShouldBeDebugTraceBlock() {
    assertThat(debugTraceBlock.getName()).isEqualTo("debug_traceBlock");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnCorrectResponse() {
    final Block parentBlock =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockHeaderFunctions(new MainnetBlockHeaderFunctions()));
    final Block block =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockHeaderFunctions(new MainnetBlockHeaderFunctions())
                    .setParentHash(parentBlock.getHash()));

    final Object[] params = new Object[] {block.toRlp().toString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlock", params));

    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getBlockByHash(block.getHeader().getParentHash()))
        .thenReturn(Optional.of(parentBlock));

    DebugTraceTransactionResult result1 = mock(DebugTraceTransactionResult.class);
    DebugTraceTransactionResult result2 = mock(DebugTraceTransactionResult.class);

    List<DebugTraceTransactionResult> resultList = Arrays.asList(result1, result2);

    try (MockedStatic<Tracer> mockedTracer = mockStatic(Tracer.class)) {
      mockedTracer
          .when(
              () ->
                  Tracer.processTracing(
                      eq(blockchainQueries), eq(block.getHash()), any(Function.class)))
          .thenReturn(Optional.of(resultList));

      final JsonRpcResponse jsonRpcResponse = debugTraceBlock.response(request);
      assertThat(jsonRpcResponse).isInstanceOf(JsonRpcSuccessResponse.class);
      JsonRpcSuccessResponse response = (JsonRpcSuccessResponse) jsonRpcResponse;

      final Collection<DebugTraceTransactionResult> traceResult = getResult(response);
      assertThat(traceResult).isNotEmpty();
      assertThat(traceResult).isInstanceOf(Collection.class).hasSize(2);
      assertThat(traceResult).containsExactly(result1, result2);
    }
  }

  @SuppressWarnings("unchecked")
  private Collection<DebugTraceTransactionResult> getResult(final JsonRpcSuccessResponse response) {
    return (Collection<DebugTraceTransactionResult>) response.getResult();
  }

  @Test
  public void shouldReturnErrorResponseWhenParentBlockMissing() {
    final Block block =
        new BlockDataGenerator()
            .block(
                BlockDataGenerator.BlockOptions.create()
                    .setBlockHeaderFunctions(new MainnetBlockHeaderFunctions()));

    final Object[] params = new Object[] {block.toRlp().toString()};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlock", params));

    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getBlockByHash(block.getHeader().getParentHash())).thenReturn(Optional.empty());

    final JsonRpcResponse jsonRpcResponse = debugTraceBlock.response(request);
    assertThat(jsonRpcResponse).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) debugTraceBlock.response(request);

    assertThat(response.getErrorType()).isEqualByComparingTo(RpcErrorType.PARENT_BLOCK_NOT_FOUND);
  }

  @Test
  public void shouldHandleInvalidParametersGracefully() {
    final Object[] invalidParams = new Object[] {"invalid RLP"};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlock", invalidParams));

    final JsonRpcResponse jsonRpcResponse = debugTraceBlock.response(request);
    assertThat(jsonRpcResponse).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse response = (JsonRpcErrorResponse) debugTraceBlock.response(request);

    assertThat(response.getError().getMessage()).contains("Invalid block, unable to parse RLP");
  }
}
