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
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthGasPriceTest {

  @Mock private PoWMiningCoordinator miningCoordinator;
  @Mock private Blockchain blockchain;
  private EthGasPrice method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_gasPrice";

  @BeforeEach
  public void setUp() {
    ApiConfiguration apiConfig = createApiConfiguration();
    method =
        new EthGasPrice(
            new BlockchainQueries(blockchain, null, Optional.empty(), Optional.empty(), apiConfig),
            miningCoordinator,
            apiConfig);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnMinValueWhenNoTransactionsExist() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = "0x4d2";
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);
    when(miningCoordinator.getMinTransactionGasPrice()).thenReturn(Wei.of(1234));

    when(blockchain.getChainHeadBlockNumber()).thenReturn(1000L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createEmptyBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(miningCoordinator).getMinTransactionGasPrice();
    verifyNoMoreInteractions(miningCoordinator);

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

    when(blockchain.getChainHeadBlockNumber()).thenReturn(1000L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createFakeBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verifyNoMoreInteractions(miningCoordinator);

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

    when(blockchain.getChainHeadBlockNumber()).thenReturn(80L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createFakeBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);

    verifyNoMoreInteractions(miningCoordinator);

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
  private void verifyGasPriceLimit(Long lowerBound, Long upperBound, long expectedGasPrice) {
    when(miningCoordinator.getMinTransactionGasPrice()).thenReturn(Wei.of(100));

    var apiConfig = createApiConfiguration(lowerBound, upperBound);
    method = createMethod(apiConfig);

    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(), Wei.of(expectedGasPrice).toShortHexString());

    when(blockchain.getChainHeadBlockNumber()).thenReturn(10L);
    when(blockchain.getBlockByNumber(anyLong()))
        .thenAnswer(invocation -> createFakeBlock(invocation.getArgument(0, Long.class)));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private Object createFakeBlock(final Long height) {
    return Optional.of(
        new Block(
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
                0,
                0,
                0,
                Bytes.EMPTY,
                Wei.ZERO,
                Hash.EMPTY,
                0,
                null,
                null,
                null,
                null,
                null,
                null),
            new BlockBody(
                List.of(
                    new Transaction.Builder()
                        .nonce(0)
                        .gasPrice(Wei.of(height * 1000000L))
                        .gasLimit(0)
                        .value(Wei.ZERO)
                        .build()),
                List.of())));
  }

  private Object createEmptyBlock(final Long height) {
    return Optional.of(
        new Block(
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
                0,
                0,
                0,
                Bytes.EMPTY,
                Wei.ZERO,
                Hash.EMPTY,
                0,
                null,
                null,
                null,
                null,
                null,
                null),
            new BlockBody(List.of(), List.of())));
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }

  private ApiConfiguration createApiConfiguration() {
    return createApiConfiguration(null, null);
  }

  private ApiConfiguration createApiConfiguration(final Long lowerBound, final Long upperBound) {
    ImmutableApiConfiguration.Builder builder =
        ImmutableApiConfiguration.builder().gasPriceMinSupplier(() -> 100);

    if (lowerBound != null) {
      builder
          .isGasAndPriorityFeeLimitingEnabled(true)
          .lowerBoundGasAndPriorityFeeCoefficient(lowerBound);
    }
    if (upperBound != null) {
      builder
          .isGasAndPriorityFeeLimitingEnabled(true)
          .upperBoundGasAndPriorityFeeCoefficient(upperBound);
    }
    return builder.build();
  }

  private EthGasPrice createMethod(final ApiConfiguration apiConfig) {
    return new EthGasPrice(
        new BlockchainQueries(blockchain, null, Optional.empty(), Optional.empty(), apiConfig),
        miningCoordinator,
        apiConfig);
  }
}
