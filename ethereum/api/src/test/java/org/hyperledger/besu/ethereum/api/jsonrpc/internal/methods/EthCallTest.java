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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.BLOCK_NOT_FOUND;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.REVERT_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.PreCloseStateHandler;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EthCallTest {

  private EthCall method;

  @Mock private Blockchain blockchain;
  @Mock ChainHead chainHead;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionSimulator transactionSimulator;

  @Mock private BlockHeader blockHeader;

  @Captor ArgumentCaptor<PreCloseStateHandler<Optional<JsonRpcResponse>>> mapperCaptor;

  @BeforeEach
  public void setUp() {
    method = new EthCall(blockchainQueries, transactionSimulator);
    blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBlockHash()).thenReturn(Hash.ZERO);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("eth_call");
  }

  @Test
  public void shouldReturnInternalErrorWhenProcessorReturnsEmpty() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, INTERNAL_ERROR);

    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator).process(any(), any(), any(), any(), any());
  }

  @Test
  public void shouldAcceptRequestWhenMissingOptionalFields() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(null, null, null, null, null, null, null, null, Boolean.FALSE, null);
    final JsonRpcRequestContext request = ethCallRequest(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Bytes.of().toString());

    mockTransactionProcessorSuccessResult(expectedResponse);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);

    final JsonRpcResponse response = method.response(request);

    final TransactionSimulatorResult result = mock(TransactionSimulatorResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getOutput()).thenReturn(Bytes.of());
    verify(transactionSimulator).process(any(), any(), any(), mapperCaptor.capture(), any());
    assertThat(mapperCaptor.getValue().apply(mock(MutableWorldState.class), Optional.of(result)))
        .isEqualTo(Optional.of(expectedResponse));

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator).process(eq(callParameter), any(), any(), any(), any());
  }

  @Test
  public void shouldReturnExecutionResultWhenExecutionIsSuccessful() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Bytes.of(1).toString());
    mockTransactionProcessorSuccessResult(expectedResponse);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    final JsonRpcResponse response = method.response(request);

    final TransactionSimulatorResult result = mock(TransactionSimulatorResult.class);
    when(result.isSuccessful()).thenReturn(true);
    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getOutput()).thenReturn(Bytes.of(1));
    verify(transactionSimulator).process(any(), any(), any(), mapperCaptor.capture(), any());
    assertThat(mapperCaptor.getValue().apply(mock(MutableWorldState.class), Optional.of(result)))
        .isEqualTo(Optional.of(expectedResponse));

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator).process(eq(callParameter()), any(), any(), any(), any());
    verify(blockchainQueries, atLeastOnce()).getBlockchain();
    verifyNoMoreInteractions(blockchainQueries);
  }

  @Test
  public void shouldReturnBasicExecutionRevertErrorWithoutReason() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");

    // Expect a revert error with no decoded reason (error doesn't begin "Error(string)" so ignored)
    final String abiHexString = "0x1234";
    final JsonRpcError expectedError = new JsonRpcError(REVERT_ERROR, abiHexString);
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, expectedError);

    assertThat(((JsonRpcErrorResponse) expectedResponse).getError().getMessage())
        .isEqualTo("Execution reverted");

    mockTransactionProcessorSuccessResult(expectedResponse);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    final JsonRpcResponse response = method.response(request);

    final TransactionProcessingResult processingResult =
        new TransactionProcessingResult(
            null, null, 0, 0, null, null, Optional.of(Bytes.fromHexString(abiHexString)));

    final TransactionSimulatorResult result = mock(TransactionSimulatorResult.class);
    when(result.isSuccessful()).thenReturn(false);
    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getResult()).thenReturn(processingResult);
    verify(transactionSimulator).process(any(), any(), any(), mapperCaptor.capture(), any());
    assertThat(mapperCaptor.getValue().apply(mock(MutableWorldState.class), Optional.of(result)))
        .isEqualTo(Optional.of(expectedResponse));

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(((JsonRpcErrorResponse) response).getError().getMessage())
        .isEqualTo("Execution reverted");
  }

  @Test
  public void shouldReturnExecutionRevertErrorWithABIParseError() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");

    // Expect a revert error with no decoded reason (error begins with "Error(string)" but trailing
    // bytes are invalid ABI)
    final String abiHexString = "0x08c379a002d36d";
    final JsonRpcError expectedError = new JsonRpcError(REVERT_ERROR, abiHexString);
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, expectedError);

    assertThat(((JsonRpcErrorResponse) expectedResponse).getError().getMessage())
        .isEqualTo("Execution reverted: ABI decode error");

    mockTransactionProcessorSuccessResult(expectedResponse);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    final JsonRpcResponse response = method.response(request);
    final TransactionProcessingResult processingResult =
        new TransactionProcessingResult(
            null, null, 0, 0, null, null, Optional.of(Bytes.fromHexString(abiHexString)));

    final TransactionSimulatorResult result = mock(TransactionSimulatorResult.class);
    when(result.isSuccessful()).thenReturn(false);
    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getResult()).thenReturn(processingResult);
    verify(transactionSimulator).process(any(), any(), any(), mapperCaptor.capture(), any());
    assertThat(mapperCaptor.getValue().apply(mock(MutableWorldState.class), Optional.of(result)))
        .isEqualTo(Optional.of(expectedResponse));

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(((JsonRpcErrorResponse) response).getError().getMessage())
        .isEqualTo("Execution reverted: ABI decode error");
  }

  @Test
  public void shouldReturnExecutionRevertErrorWithParsedABI() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");

    // Expect a revert error with decoded reason (error begins with "Error(string)", trailing bytes
    // = "ERC20: transfer from the zero address")
    final String abiHexString =
        "0x08c379a00000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002545524332303a207472616e736665722066726f6d20746865207a65726f2061646472657373000000000000000000000000000000000000000000000000000000";
    final JsonRpcError expectedError = new JsonRpcError(REVERT_ERROR, abiHexString);
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, expectedError);

    assertThat(((JsonRpcErrorResponse) expectedResponse).getError().getMessage())
        .isEqualTo("Execution reverted: ERC20: transfer from the zero address");

    mockTransactionProcessorSuccessResult(expectedResponse);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    final JsonRpcResponse response = method.response(request);

    final TransactionProcessingResult processingResult =
        new TransactionProcessingResult(
            null, null, 0, 0, null, null, Optional.of(Bytes.fromHexString(abiHexString)));

    final TransactionSimulatorResult result = mock(TransactionSimulatorResult.class);
    when(result.isSuccessful()).thenReturn(false);
    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getResult()).thenReturn(processingResult);

    verify(transactionSimulator).process(any(), any(), any(), mapperCaptor.capture(), any());
    System.out.println(result);
    System.out.println(expectedResponse);
    assertThat(mapperCaptor.getValue().apply(mock(MutableWorldState.class), Optional.of(result)))
        .isEqualTo(Optional.of(expectedResponse));

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(((JsonRpcErrorResponse) response).getError().getMessage())
        .isEqualTo("Execution reverted: ERC20: transfer from the zero address");
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenLatest() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ZERO));
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    method.response(request);

    verify(blockchainQueries, atLeastOnce()).getBlockchain();
    verify(transactionSimulator).process(any(), any(), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenEarliest() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "earliest");
    when(blockchainQueries.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(Hash.ZERO));
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(eq(Hash.ZERO));
    verify(transactionSimulator).process(any(), any(), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenSafe() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "safe");
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO)).thenReturn(Optional.of(blockHeader));
    when(blockchainQueries.safeBlockHeader()).thenReturn(Optional.of(blockHeader));
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(Hash.ZERO);
    verify(blockchainQueries).safeBlockHeader();
    verify(transactionSimulator).process(any(), any(), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenFinalized() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "finalized");
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO)).thenReturn(Optional.of(blockHeader));
    when(blockchainQueries.finalizedBlockHeader()).thenReturn(Optional.of(blockHeader));
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(Hash.ZERO);
    verify(blockchainQueries).finalizedBlockHeader();
    verify(transactionSimulator).process(any(), any(), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenSpecified() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), Quantity.create(13L));
    when(blockchainQueries.headBlockNumber()).thenReturn(14L);
    when(blockchainQueries.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(Hash.ZERO));
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(eq(Hash.ZERO));
    verify(transactionSimulator).process(any(), any(), any(), any(), any());
  }

  @Test
  public void shouldReturnBlockNotFoundWhenInvalidBlockNumberSpecified() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), Quantity.create(33L));
    when(blockchainQueries.headBlockNumber()).thenReturn(14L);
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, BLOCK_NOT_FOUND);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(blockchainQueries).headBlockNumber();
  }

  @Test
  public void shouldAutoSelectIsAllowedExceedingBalanceToTrueWhenGasPriceIsZero() {
    final JsonCallParameter callParameters = callParameter(Wei.ZERO, null, null);
    internalAutoSelectIsAllowedExceedingBalance(callParameters, Optional.empty(), true);
  }

  @Test
  public void shouldAutoSelectIsAllowedExceedingBalanceToTrueWhenGasPriceIsZeroAfterEIP1559() {
    final JsonCallParameter callParameters = callParameter(Wei.ZERO, null, null);
    internalAutoSelectIsAllowedExceedingBalance(callParameters, Optional.of(Wei.ONE), true);
  }

  @Test
  public void shouldAutoSelectIsAllowedExceedingBalanceToFalseWhenGasPriceIsNotZero() {
    final JsonCallParameter callParameters = callParameter(Wei.ONE, null, null);
    internalAutoSelectIsAllowedExceedingBalance(callParameters, Optional.empty(), false);
  }

  @Test
  public void shouldAutoSelectIsAllowedExceedingBalanceToFalseWhenGasPriceIsNotZeroAfterEIP1559() {
    final JsonCallParameter callParameters = callParameter(Wei.ONE, null, null);
    internalAutoSelectIsAllowedExceedingBalance(callParameters, Optional.of(Wei.ONE), false);
  }

  @Test
  public void shouldAutoSelectIsAllowedExceedingBalanceToTrueWhenFeesAreZero() {
    final JsonCallParameter callParameters = callParameter(null, Wei.ZERO, Wei.ZERO);
    internalAutoSelectIsAllowedExceedingBalance(callParameters, Optional.of(Wei.ONE), true);
  }

  @Test
  public void shouldAutoSelectIsAllowedExceedingBalanceToFalseWhenFeesAreZero() {
    final JsonCallParameter callParameters = callParameter(null, Wei.ONE, Wei.ONE);
    internalAutoSelectIsAllowedExceedingBalance(callParameters, Optional.of(Wei.ONE), false);
  }

  private void internalAutoSelectIsAllowedExceedingBalance(
      final JsonCallParameter callParameter,
      final Optional<Wei> baseFee,
      final boolean isAllowedExceedingBalance) {
    final JsonRpcRequestContext request = ethCallRequest(callParameter, "latest");

    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(baseFee);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHead()).thenReturn(chainHead);
    when(chainHead.getBlockHeader()).thenReturn(blockHeader);

    method.response(request);

    final TransactionValidationParams transactionValidationParams =
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator())
            .isAllowExceedingBalance(isAllowedExceedingBalance)
            .build();

    verify(transactionSimulator)
        .process(any(), eq(transactionValidationParams), any(), any(), any());
  }

  private JsonCallParameter callParameter() {
    return callParameter(Wei.ZERO, null, null);
  }

  private JsonCallParameter callParameter(
      final Wei gasPrice, final Wei maxFeesPerGas, final Wei maxPriorityFeesPerGas) {
    return new JsonCallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        0L,
        gasPrice,
        maxFeesPerGas,
        maxPriorityFeesPerGas,
        Wei.ZERO,
        Bytes.EMPTY,
        null,
        null);
  }

  private JsonRpcRequestContext ethCallRequest(
      final CallParameter callParameter, final String blockNumberInHex) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eth_call", new Object[] {callParameter, blockNumberInHex}));
  }

  private void mockTransactionProcessorSuccessResult(final JsonRpcResponse jsonRpcResponse) {
    when(transactionSimulator.process(any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(jsonRpcResponse));
  }
}
