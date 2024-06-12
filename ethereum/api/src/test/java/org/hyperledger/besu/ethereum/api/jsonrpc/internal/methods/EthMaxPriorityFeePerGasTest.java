/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthMaxPriorityFeePerGasTest {
  private static final String JSON_RPC_VERSION = "2.0";
  private static final String ETH_METHOD =
      RpcMethod.ETH_GET_MAX_PRIORITY_FEE_PER_GAS.getMethodName();
  private EthMaxPriorityFeePerGas method;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private MiningCoordinator miningCoordinator;

  @BeforeEach
  public void setUp() {
    method = createEthMaxPriorityFeePerGasMethod();
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void whenNoTransactionsExistReturnMinPriorityFeePerGasPrice() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = Wei.ONE.toShortHexString();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);
    when(miningCoordinator.getMinPriorityFeePerGas()).thenReturn(Wei.ONE);

    mockBlockchainQueriesMaxPriorityFeePerGasPrice(Optional.empty());
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(miningCoordinator, VerificationModeFactory.times(1)).getMinPriorityFeePerGas();
  }

  @ParameterizedTest
  @MethodSource("minPriorityFeePerGasValues")
  public void whenNoTransactionsExistReturnMinPriorityFeePerGasPriceExist(
      final Wei minPriorityFeePerGasValue) {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = minPriorityFeePerGasValue.toShortHexString();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);
    when(miningCoordinator.getMinPriorityFeePerGas()).thenReturn(minPriorityFeePerGasValue);

    mockBlockchainQueriesMaxPriorityFeePerGasPrice(Optional.empty());
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(miningCoordinator, VerificationModeFactory.times(1)).getMinPriorityFeePerGas();
  }

  @Test
  public void whenNoTransactionsExistReturnNullMinPriorityFeePerGasPriceExist() {
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), null);
    when(miningCoordinator.getMinPriorityFeePerGas()).thenReturn(null);

    mockBlockchainQueriesMaxPriorityFeePerGasPrice(Optional.empty());
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(miningCoordinator, VerificationModeFactory.times(1)).getMinPriorityFeePerGas();
  }

  @Test
  public void whenTransactionsExistReturnMaxPriorityFeePerGasPrice() {
    final JsonRpcRequestContext request = requestWithParams();
    final String expectedWei = Wei.of(2000000000).toShortHexString();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), expectedWei);
    mockBlockchainQueriesMaxPriorityFeePerGasPrice(Optional.of(Wei.of(2000000000)));
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(miningCoordinator, VerificationModeFactory.times(0)).getMinPriorityFeePerGas();
  }

  private static Stream<Arguments> minPriorityFeePerGasValues() {
    return Stream.of(Arguments.of(Wei.ONE), Arguments.of(Wei.ZERO));
  }

  private void mockBlockchainQueriesMaxPriorityFeePerGasPrice(final Optional<Wei> result) {
    when(blockchainQueries.gasPriorityFee()).thenReturn(result);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }

  private EthMaxPriorityFeePerGas createEthMaxPriorityFeePerGasMethod() {
    return new EthMaxPriorityFeePerGas(blockchainQueries, miningCoordinator);
  }
}
