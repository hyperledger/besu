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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.CreateAccessListResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.AccessListEntry;
import org.hyperledger.besu.evm.tracing.AccessListOperationTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EthCreateAccessListTest {

  private final String METHOD = "eth_createAccessList";
  private EthCreateAccessList method;

  @Mock private BlockHeader blockHeader;
  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionSimulator transactionSimulator;
  @Mock private WorldStateArchive worldStateArchive;

  @BeforeEach
  public void setUp() {
    when(blockchainQueries.headBlockNumber()).thenReturn(1L);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(blockchain.getBlockHeader(eq(1L))).thenReturn(Optional.of(blockHeader));
    when(blockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(blockHeader.getNumber()).thenReturn(1L);
    when(worldStateArchive.isWorldStateAvailable(any(), any())).thenReturn(true);

    method = new EthCreateAccessList(blockchainQueries, transactionSimulator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(METHOD);
  }

  private JsonRpcRequestContext ethCreateAccessListRequest(final CallParameter callParameter) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", METHOD, new Object[] {callParameter}));
  }

  @Test
  public void shouldReturnGasEstimateWhenTransientLegacyTransactionProcessorReturnsResultSuccess() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO));
    mockTransactionSimulatorResult(true, false, 1L);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(new ArrayList<>(), 1L));

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldUseGasPriceParameterWhenIsPresent() {
    final Wei gasPrice = Wei.of(1000);
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(gasPrice));
    mockTransactionSimulatorResult(true, false, 1L);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(new ArrayList<>(), 1L));

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnGasEstimateErrorWhenGasPricePresentForEip1559Transaction() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(Optional.of(Wei.of(10))));
    mockTransactionSimulatorResult(false, false, 1L);

    Assertions.assertThatThrownBy(() -> method.response(request))
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessageContaining("gasPrice cannot be used with baseFee or maxFeePerGas");
  }

  @Test
  public void shouldReturnErrorWhenWorldStateIsNotAvailable() {
    when(worldStateArchive.isWorldStateAvailable(any(), any())).thenReturn(false);
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO));
    mockTransactionSimulatorResult(false, false, 1L);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.WORLD_STATE_UNAVAILABLE);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenTransactionReverted() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO));
    mockTransactionSimulatorResult(false, true, 1L);

    final String errorReason = "0x00";
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, new JsonRpcError(RpcErrorType.REVERT_ERROR, errorReason));

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyAccessListIfNoParameterAndWithoutAccessedStorage() {
    final List<AccessListEntry> expectedAccessList = new ArrayList<>();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter());
    mockTransactionSimulatorResult(true, false, 1L);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(1)).process(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldReturnAccessListIfNoParameterAndWithAccessedStorage() {
    // Create a 1559 call without access lists
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter());
    // Create a list with one access list entry
    final List<AccessListEntry> expectedAccessList = createAccessList();

    // expect a list with the mocked access list
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final AccessListOperationTracer tracer = createMockTracer(expectedAccessList);

    // Set TransactionSimulator.process response
    mockTransactionSimulatorResult(true, false, 1L);
    Assertions.assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(2)).process(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldReturnEmptyAccessListIfNoAccessedStorage() {
    // Create a list with one enter
    final List<AccessListEntry> accessListParam = createAccessList();
    // expect empty list
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(new ArrayList<>(), 1L));
    // create a request using the accessListParam
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(accessListParam));

    // Set TransactionSimulator.process response
    mockTransactionSimulatorResult(true, false, 1L);
    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(1)).process(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldReturnAccessListIfParameterAndSameAccessedStorage() {
    // Create a list with one access list entry
    final List<AccessListEntry> expectedAccessList = createAccessList();
    // Create a 1559 call with the expected access lists
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(expectedAccessList));

    // expect a list with the mocked access list
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final AccessListOperationTracer tracer = createMockTracer(expectedAccessList);

    // Set TransactionSimulator.process response
    mockTransactionSimulatorResult(true, false, 1L);
    Assertions.assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(1)).process(any(), any(), any(), anyLong());
  }

  @Test
  public void shouldReturnAccessListIfWithParameterAndDifferentAccessedStorage() {
    // Create a list with one access list entry
    final List<AccessListEntry> accessListParam = createAccessList();
    // Create a 1559 call with the accessListParam
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(accessListParam));

    // Create a list with one access list entry
    final List<AccessListEntry> expectedAccessList = createAccessList();

    // expect a list with the mocked access list
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final AccessListOperationTracer tracer = createMockTracer(expectedAccessList);

    // Set TransactionSimulator.process response
    mockTransactionSimulatorResult(true, false, 1L);
    Assertions.assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(2)).process(any(), any(), any(), anyLong());
  }

  private JsonRpcResponse responseWithMockTracer(
      final JsonRpcRequestContext request, final AccessListOperationTracer tracer) {
    try (final MockedStatic<AccessListOperationTracer> tracerMockedStatic =
        Mockito.mockStatic(AccessListOperationTracer.class)) {
      tracerMockedStatic.when(AccessListOperationTracer::create).thenReturn(tracer);
      return method.response(request);
    }
  }

  private AccessListOperationTracer createMockTracer(
      final List<AccessListEntry> accessListEntries) {
    final AccessListOperationTracer tracer = mock(AccessListOperationTracer.class);
    when(tracer.getAccessList()).thenReturn(accessListEntries);
    return tracer;
  }

  private void mockTransactionSimulatorResult(
      final boolean isSuccessful, final boolean isReverted, final long estimateGas) {
    final TransactionSimulatorResult mockTxSimResult = mock(TransactionSimulatorResult.class);
    when(transactionSimulator.process(any(), any(), any(), anyLong()))
        .thenReturn(Optional.of(mockTxSimResult));
    final TransactionProcessingResult mockResult = mock(TransactionProcessingResult.class);
    when(mockResult.getEstimateGasUsedByTransaction()).thenReturn(estimateGas);
    when(mockResult.getRevertReason())
        .thenReturn(isReverted ? Optional.of(Bytes.of(0)) : Optional.empty());
    when(mockTxSimResult.getResult()).thenReturn(mockResult);
    when(mockTxSimResult.isSuccessful()).thenReturn(isSuccessful);
  }

  private JsonCallParameter legacyTransactionCallParameter(final Wei gasPrice) {
    return new JsonCallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        0L,
        gasPrice,
        null,
        null,
        Wei.ZERO,
        Bytes.EMPTY,
        false,
        null);
  }

  private CallParameter eip1559TransactionCallParameter() {
    return eip1559TransactionCallParameter(Optional.empty(), null);
  }

  private CallParameter eip1559TransactionCallParameter(final Optional<Wei> gasPrice) {
    return eip1559TransactionCallParameter(gasPrice, null);
  }

  private CallParameter eip1559TransactionCallParameter(
      final List<AccessListEntry> accessListEntries) {
    return eip1559TransactionCallParameter(Optional.empty(), accessListEntries);
  }

  private JsonCallParameter eip1559TransactionCallParameter(
      final Optional<Wei> gasPrice, final List<AccessListEntry> accessListEntries) {
    return new JsonCallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        null,
        gasPrice.orElse(null),
        Wei.fromHexString("0x10"),
        Wei.fromHexString("0x10"),
        Wei.ZERO,
        Bytes.EMPTY,
        false,
        accessListEntries);
  }

  private List<AccessListEntry> createAccessList() {
    return List.of(
        new AccessListEntry(Address.wrap(Bytes.random(Address.SIZE)), List.of(Bytes32.random())));
  }
}
