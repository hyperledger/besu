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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.LondonFeeMarket;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthGasPriceTest {
  private static final String JSON_RPC_VERSION = "2.0";
  private static final String ETH_METHOD = "eth_gasPrice";
  private static final long DEFAULT_BLOCK_GAS_LIMIT = 100_000;
  private static final long DEFAULT_BLOCK_GAS_USED = 21_000;

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private Blockchain blockchain;
  private EthGasPrice method;
  private MiningParameters miningParameters;

  @BeforeEach
  public void setUp() {
    ApiConfiguration apiConfig = createDefaultApiConfiguration();
    miningParameters = MiningParameters.newDefault();
    method = createEthGasPriceMethod(apiConfig);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnMinValueWhenNoTransactionsExist() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x4d2";
    miningParameters.setMinTransactionGasPrice(Wei.fromHexString(expectedWei));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);

    mockBaseFeeMarket();

    mockBlockchain(1000, 0);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(blockchain).getChainHeadBlockNumber();
    verify(blockchain, VerificationModeFactory.times(100)).getBlockByNumber(anyLong());
    verifyNoMoreInteractions(blockchain);
  }

  @Test
  public void shouldReturnMedianWhenTransactionsExist() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x389fd980"; // 950Wei, gas prices are 900-999 wei.
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);

    mockBaseFeeMarket();

    mockBlockchain(1000L, 1);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(blockchain).getChainHeadBlockNumber();
    verify(blockchain, VerificationModeFactory.times(100)).getBlockByNumber(anyLong());
    verifyNoMoreInteractions(blockchain);
  }

  @Test
  public void shortChainQueriesAllBlocks() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x2625a00";
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);

    mockBaseFeeMarket();

    mockBlockchain(80L, 1);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(blockchain).getChainHeadBlockNumber();
    verify(blockchain, VerificationModeFactory.times(80)).getBlockByNumber(anyLong());
    verifyNoMoreInteractions(blockchain);
  }

  /**
   * Test to verify that the method returns the lower bound gas price when the lower bound parameter
   * is present.
   */
  @Test
  public void shouldReturnLimitedPriceWhenLowerBoundIsPresent() {
    long gasPrice = 5000000;
    long lowerBoundGasPrice = gasPrice + 1;
    verifyGasPriceLimit(lowerBoundGasPrice, null, lowerBoundGasPrice);
  }

  /**
   * Test to verify that the method returns the upper bound gas price when the upper bound parameter
   * is present.
   */
  @Test
  public void shouldReturnLimitedPriceWhenUpperBoundIsPresent() {
    long gasPrice = 5000000;
    long upperBoundGasPrice = gasPrice - 1;
    verifyGasPriceLimit(null, upperBoundGasPrice, upperBoundGasPrice);
  }

  /**
   * Test to verify that the method returns the actual gas price when the gas price is within the
   * bound range.
   */
  @Test
  public void shouldReturnActualGasPriceWhenWithinBoundRange() {
    long gasPrice = 5000000;
    long lowerBoundGasPrice = gasPrice - 1;
    long upperBoundGasPrice = gasPrice + 1;
    verifyGasPriceLimit(lowerBoundGasPrice, upperBoundGasPrice, gasPrice);
  }

  /**
   * Helper method to verify the gas price limit.
   *
   * @param lowerBound The lower bound of the gas price.
   * @param upperBound The upper bound of the gas price.
   * @param expectedGasPrice The expected gas price.
   */
  private void verifyGasPriceLimit(
      final Long lowerBound, final Long upperBound, final long expectedGasPrice) {
    miningParameters.setMinTransactionGasPrice(Wei.of(100));

    mockBaseFeeMarket();

    var apiConfig =
        createApiConfiguration(Optional.ofNullable(lowerBound), Optional.ofNullable(upperBound));
    method = createEthGasPriceMethod(apiConfig);

    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(), Wei.of(expectedGasPrice).toShortHexString());

    final var chainHeadBlockNumber = 10L;
    mockBlockchain(chainHeadBlockNumber, 1);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private void mockBaseFeeMarket() {
    final var baseFeeMarket = new LondonFeeMarket(0);
    final var protocolSpec = mock(ProtocolSpec.class);
    when(protocolSpec.getFeeMarket()).thenReturn(baseFeeMarket);
    when(protocolSchedule.getForNextBlockHeader(any(), anyLong())).thenReturn(protocolSpec);
  }

  private void mockBlockchain(final long chainHeadBlockNumber, final int txsNum) {
    final var blocksByNumber = new HashMap<Long, Block>();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(chainHeadBlockNumber);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    blocksByNumber.computeIfAbsent(
                        invocation.getArgument(0, Long.class),
                        blockNumber -> createFakeBlock(blockNumber, txsNum))));

    when(blockchain.getChainHeadHeader())
        .thenReturn(
            blocksByNumber
                .computeIfAbsent(
                    chainHeadBlockNumber, blockNumber -> createFakeBlock(blockNumber, txsNum))
                .getHeader());
  }

  private Block createFakeBlock(final long height, final int txsNum) {
    return createFakeBlock(height, txsNum, DEFAULT_BLOCK_GAS_LIMIT, DEFAULT_BLOCK_GAS_USED);
  }

  private Block createFakeBlock(
      final long height, final int txsNum, final long gasLimit, final long gasUsed) {
    return new Block(
        new BlockHeader(
            Hash.EMPTY,
            Hash.EMPTY_TRIE_HASH,
            Address.ZERO,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY_TRIE_HASH,
            Hash.EMPTY_TRIE_HASH,
            LogsBloomFilter.builder().build(),
            Difficulty.ONE,
            height,
            gasLimit,
            gasUsed,
            0,
            Bytes.EMPTY,
            Wei.of(100),
            Hash.EMPTY,
            0,
            null,
            null,
            null,
            null,
            null,
            null),
        new BlockBody(
            IntStream.range(0, txsNum)
                .mapToObj(
                    i ->
                        new Transaction.Builder()
                            .nonce(i)
                            .gasPrice(Wei.of(height * 1000000L))
                            .gasLimit(gasUsed)
                            .value(Wei.ZERO)
                            .build())
                .toList(),
            List.of()));
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }

  private ApiConfiguration createDefaultApiConfiguration() {
    return createApiConfiguration(Optional.empty(), Optional.empty());
  }

  private ApiConfiguration createApiConfiguration(
      final Optional<Long> lowerBound, final Optional<Long> upperBound) {
    ImmutableApiConfiguration.Builder builder = ImmutableApiConfiguration.builder();

    lowerBound.ifPresent(
        value ->
            builder
                .isGasAndPriorityFeeLimitingEnabled(true)
                .lowerBoundGasAndPriorityFeeCoefficient(value));
    upperBound.ifPresent(
        value ->
            builder
                .isGasAndPriorityFeeLimitingEnabled(true)
                .upperBoundGasAndPriorityFeeCoefficient(value));

    return builder.build();
  }

  private EthGasPrice createEthGasPriceMethod(final ApiConfiguration apiConfig) {
    return new EthGasPrice(
        new BlockchainQueries(
            protocolSchedule,
            blockchain,
            null,
            Optional.empty(),
            Optional.empty(),
            apiConfig,
            miningParameters),
        apiConfig);
  }
}
