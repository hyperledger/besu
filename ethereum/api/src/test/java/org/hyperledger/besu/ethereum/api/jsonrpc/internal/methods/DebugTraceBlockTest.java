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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidatorFactory;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsProcessor;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DebugTraceBlockTest {
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private Blockchain blockchain;
  @Mock private WithdrawalsProcessor withdrawalsProcessor;
  @Mock private TransactionValidatorFactory alwaysValidTransactionValidatorFactory;
  private DebugTraceBlock debugTraceBlock;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

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
                        false,
                        EvmConfiguration.DEFAULT,
                        MiningConfiguration.MINING_DISABLED,
                        new BadBlockManager(),
                        false,
                        BalConfiguration.DEFAULT,
                        new NoOpMetricsSystem())
                    .createProtocolSchedule())
            .build();
    debugTraceBlock =
        new DebugTraceBlock(executionContextTestFixture.getProtocolSchedule(), blockchainQueries);
  }

  @Test
  public void nameShouldBeDebugTraceBlock() {
    assertThat(debugTraceBlock.getName()).isEqualTo("debug_traceBlock");
  }

  @Test
  public void shouldReturnCorrectResponse() throws IOException {
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

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    debugTraceBlock.streamResponse(request, out, mapper);
    final String json = out.toString(UTF_8);
    assertThat(json).startsWith("{\"jsonrpc\":\"2.0\"");
    assertThat(json).contains("\"result\":");
  }

  @Test
  public void shouldReturnErrorResponseWhenParentBlockMissing() throws IOException {
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

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    debugTraceBlock.streamResponse(request, out, mapper);
    final JsonNode response = mapper.readTree(out.toByteArray());
    assertThat(response.has("error")).isTrue();
    assertThat(response.get("error").get("message").asText()).contains("Parent block not found");
  }

  @Test
  public void shouldHandleInvalidParametersGracefully() throws IOException {
    final Object[] invalidParams = new Object[] {"invalid RLP"};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(new JsonRpcRequest("2.0", "debug_traceBlock", invalidParams));

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    debugTraceBlock.streamResponse(request, out, mapper);
    final JsonNode response = mapper.readTree(out.toByteArray());
    assertThat(response.has("error")).isTrue();
    assertThat(response.get("error").get("message").asText())
        .contains("Invalid block param (block not found)");
  }
}
