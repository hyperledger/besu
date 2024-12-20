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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.AccessListEntry;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
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
import org.hyperledger.besu.evm.tracing.AccessListOperationTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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

  @Mock private BlockHeader latestBlockHeader;
  @Mock private BlockHeader finalizedBlockHeader;
  @Mock private BlockHeader genesisBlockHeader;
  @Mock private BlockHeader pendingBlockHeader;
  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionSimulator transactionSimulator;
  @Mock private WorldStateArchive worldStateArchive;

  @BeforeEach
  public void setUp() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(blockchainQueries.headBlockNumber()).thenReturn(2L);
    when(blockchainQueries.getBlockHeaderByNumber(0L)).thenReturn(Optional.of(genesisBlockHeader));
    when(blockchainQueries.finalizedBlockHeader()).thenReturn(Optional.of(finalizedBlockHeader));
    when(blockchainQueries.getBlockHeaderByNumber(1L))
        .thenReturn(Optional.of(finalizedBlockHeader));
    when(genesisBlockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(genesisBlockHeader.getNumber()).thenReturn(0L);
    when(finalizedBlockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(finalizedBlockHeader.getNumber()).thenReturn(1L);
    when(blockchain.getChainHeadHeader()).thenReturn(latestBlockHeader);
    when(latestBlockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(latestBlockHeader.getNumber()).thenReturn(2L);
    when(pendingBlockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(pendingBlockHeader.getNumber()).thenReturn(3L);
    when(transactionSimulator.simulatePendingBlockHeader()).thenReturn(pendingBlockHeader);
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

  private JsonRpcRequestContext ethCreateAccessListRequest(
      final CallParameter callParameter, final String blockParam) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", METHOD, new Object[] {callParameter, blockParam}));
  }

  @Test
  public void shouldReturnGasEstimateWhenTransientLegacyTransactionProcessorReturnsResultSuccess() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO));
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(new ArrayList<>(), 1L));

    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldUseGasPriceParameterWhenIsPresent() {
    final Wei gasPrice = Wei.of(1000);
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(gasPrice));
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(new ArrayList<>(), 1L));

    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void pendingBlockTagEstimateOnPendingBlock() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO), "pending");
    mockTransactionSimulatorResult(true, false, 1L, pendingBlockHeader);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(new ArrayList<>(), 1L));

    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldNotErrorWhenGasPricePresentForEip1559Transaction() {
    final Wei gasPrice = Wei.of(1000);
    final List<AccessListEntry> expectedAccessList = new ArrayList<>();
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(Optional.of(gasPrice)));
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenWorldStateIsNotAvailable() {
    when(worldStateArchive.isWorldStateAvailable(any(), any())).thenReturn(false);
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO));
    mockTransactionSimulatorResult(false, false, 1L, latestBlockHeader);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.WORLD_STATE_UNAVAILABLE);

    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenTransactionReverted() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(legacyTransactionCallParameter(Wei.ZERO));
    mockTransactionSimulatorResult(false, true, 1L, latestBlockHeader);

    final String errorReason = "0x00";
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, new JsonRpcError(RpcErrorType.REVERT_ERROR, errorReason));

    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnEmptyAccessListIfNoParameterAndWithoutAccessedStorage() {
    final List<AccessListEntry> expectedAccessList = new ArrayList<>();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter());
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);

    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator, times(1))
        .process(any(), eq(Optional.empty()), any(), any(), eq(latestBlockHeader));
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
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);
    assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(2))
        .process(any(), eq(Optional.empty()), any(), any(), eq(latestBlockHeader));
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
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);
    assertThat(method.response(request)).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator, times(1))
        .process(any(), eq(Optional.empty()), any(), any(), eq(latestBlockHeader));
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
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);
    assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(1))
        .process(any(), eq(Optional.empty()), any(), any(), eq(latestBlockHeader));
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
    mockTransactionSimulatorResult(true, false, 1L, latestBlockHeader);
    assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(2))
        .process(any(), eq(Optional.empty()), any(), any(), eq(latestBlockHeader));
  }

  @Test
  public void shouldReturnAccessListWhenBlockTagParamIsPresent() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(), "finalized");
    // Create a list with one access list entry
    final List<AccessListEntry> expectedAccessList = createAccessList();

    // expect a list with the mocked access list
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final AccessListOperationTracer tracer = createMockTracer(expectedAccessList);

    // Set TransactionSimulator.process response
    mockTransactionSimulatorResult(true, false, 1L, finalizedBlockHeader);
    assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(2))
        .process(any(), eq(Optional.empty()), any(), any(), eq(finalizedBlockHeader));
  }

  @Test
  public void shouldReturnAccessListWhenBlockNumberParamIsPresent() {
    final JsonRpcRequestContext request =
        ethCreateAccessListRequest(eip1559TransactionCallParameter(), "0x0");
    // Create a list with one access list entry
    final List<AccessListEntry> expectedAccessList = createAccessList();

    // expect a list with the mocked access list
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, new CreateAccessListResult(expectedAccessList, 1L));
    final AccessListOperationTracer tracer = createMockTracer(expectedAccessList);

    // Set TransactionSimulator.process response
    mockTransactionSimulatorResult(true, false, 1L, genesisBlockHeader);
    assertThat(responseWithMockTracer(request, tracer))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
    verify(transactionSimulator, times(2))
        .process(any(), eq(Optional.empty()), any(), any(), eq(genesisBlockHeader));
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

  @SuppressWarnings("ReferenceEquality")
  private void mockTransactionSimulatorResult(
      final boolean isSuccessful,
      final boolean isReverted,
      final long estimateGas,
      final BlockHeader blockHeader) {
    final TransactionSimulatorResult mockTxSimResult = mock(TransactionSimulatorResult.class);
    if (blockHeader == pendingBlockHeader) {
      when(transactionSimulator.processOnPending(
              any(), eq(Optional.empty()), any(), any(), eq(blockHeader)))
          .thenReturn(Optional.of(mockTxSimResult));
    } else {
      when(transactionSimulator.process(any(), eq(Optional.empty()), any(), any(), eq(blockHeader)))
          .thenReturn(Optional.of(mockTxSimResult));
    }
    final TransactionProcessingResult mockResult = mock(TransactionProcessingResult.class);
    when(mockResult.getEstimateGasUsedByTransaction()).thenReturn(estimateGas);
    when(mockResult.getRevertReason())
        .thenReturn(isReverted ? Optional.of(Bytes.of(0)) : Optional.empty());
    when(mockTxSimResult.result()).thenReturn(mockResult);
    when(mockTxSimResult.isSuccessful()).thenReturn(isSuccessful);
  }

  private JsonCallParameter legacyTransactionCallParameter(final Wei gasPrice) {
    return new JsonCallParameter.JsonCallParameterBuilder()
        .withFrom(Address.fromHexString("0x0"))
        .withTo(Address.fromHexString("0x0"))
        .withGas(0L)
        .withGasPrice(gasPrice)
        .withValue(Wei.ZERO)
        .withInput(Bytes.EMPTY)
        .withStrict(Boolean.FALSE)
        .build();
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
    return new JsonCallParameter.JsonCallParameterBuilder()
        .withFrom(Address.fromHexString("0x0"))
        .withTo(Address.fromHexString("0x0"))
        .withGasPrice(gasPrice.orElse(null))
        .withMaxFeePerGas(Wei.fromHexString("0x10"))
        .withMaxPriorityFeePerGas(Wei.fromHexString("0x10"))
        .withValue(Wei.ZERO)
        .withInput(Bytes.EMPTY)
        .withStrict(Boolean.FALSE)
        .withAccessList(accessListEntries)
        .build();
  }

  private List<AccessListEntry> createAccessList() {
    return List.of(
        new AccessListEntry(Address.wrap(Bytes.random(Address.SIZE)), List.of(Bytes32.random())));
  }
}
