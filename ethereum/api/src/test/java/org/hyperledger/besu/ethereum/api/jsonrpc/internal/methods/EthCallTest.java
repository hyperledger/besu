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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.BLOCK_NOT_FOUND;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthCallTest {

  private EthCall method;

  @Mock private Blockchain blockchain;
  @Mock ChainHead chainHead;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionSimulator transactionSimulator;

  @Before
  public void setUp() {
    method = new EthCall(blockchainQueries, transactionSimulator);
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
    when(blockchainQueries.getBlockchain().getChainHead()).thenReturn(chainHead);
    when(blockchainQueries.getBlockchain().getChainHead().getHash()).thenReturn(Hash.ZERO);
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(transactionSimulator.process(any(), any(), any(), any())).thenReturn(Optional.empty());

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator).process(any(), any(), any(), any());
  }

  @Test
  public void shouldAcceptRequestWhenMissingOptionalFields() {
    final JsonCallParameter callParameter =
        new JsonCallParameter(null, null, null, null, null, null, null, null, null);
    final JsonRpcRequestContext request = ethCallRequest(callParameter, "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Bytes.of().toString());

    mockTransactionProcessorSuccessResult(Bytes.of());
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getBlockchain().getChainHead()).thenReturn(chainHead);
    when(blockchainQueries.getBlockchain().getChainHead().getHash()).thenReturn(Hash.ZERO);
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator).process(eq(callParameter), any(), any(), any());
  }

  @Test
  public void shouldReturnExecutionResultWhenExecutionIsSuccessful() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Bytes.of(1).toString());
    mockTransactionProcessorSuccessResult(Bytes.of(1));
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getBlockchain().getChainHead()).thenReturn(chainHead);
    when(blockchainQueries.getBlockchain().getChainHead().getHash()).thenReturn(Hash.ZERO);
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionSimulator).process(eq(callParameter()), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenLatest() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "latest");
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getBlockchain().getChainHead()).thenReturn(chainHead);
    when(blockchainQueries.getBlockchain().getChainHead().getHash()).thenReturn(Hash.ZERO);
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(transactionSimulator.process(any(), any(), any(), any())).thenReturn(Optional.empty());

    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(eq(Hash.ZERO));
    verify(transactionSimulator).process(any(), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenEarliest() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), "earliest");
    when(blockchainQueries.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(Hash.ZERO));
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(transactionSimulator.process(any(), any(), any(), any())).thenReturn(Optional.empty());
    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(eq(Hash.ZERO));
    verify(transactionSimulator).process(any(), any(), any(), any());
  }

  @Test
  public void shouldUseCorrectBlockNumberWhenSpecified() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), Quantity.create(13L));
    when(blockchainQueries.headBlockNumber()).thenReturn(14L);
    when(blockchainQueries.getBlockHashByNumber(anyLong())).thenReturn(Optional.of(Hash.ZERO));
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO))
        .thenReturn(Optional.of(mock(BlockHeader.class)));
    when(transactionSimulator.process(any(), any(), any(), any())).thenReturn(Optional.empty());

    method.response(request);

    verify(blockchainQueries).getBlockHeaderByHash(eq(Hash.ZERO));
    verify(transactionSimulator).process(any(), any(), any(), any());
  }

  @Test
  public void shouldReturnBlockNotFoundWhenInvalidBlockNumberSpecified() {
    final JsonRpcRequestContext request = ethCallRequest(callParameter(), Quantity.create(33L));
    when(blockchainQueries.headBlockNumber()).thenReturn(14L);
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, BLOCK_NOT_FOUND);

    JsonRpcResponse response = method.response(request);
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);

    verify(blockchainQueries).headBlockNumber();
  }

  @Test
  public void shouldAutoSelectIsAllowedExeceedingBalanceToTrueWhenGasPriceIsZero() {
    JsonCallParameter callParameters = callParameter(Wei.ZERO, null, null);
    internalAutoSelectIsAllowedExeecdBalance(callParameters, Optional.empty(), true);
  }

  @Test
  public void shouldAutoSelectIsAllowedExeceedingBalanceToTrueWhenGasPriceIsZeroAfterEIP1559() {
    JsonCallParameter callParameters = callParameter(Wei.ZERO, null, null);
    internalAutoSelectIsAllowedExeecdBalance(callParameters, Optional.of(Wei.ONE), true);
  }

  @Test
  public void shouldAutoSelectIsAllowedExeceedingBalanceToFalseWhenGasPriceIsNotZero() {
    JsonCallParameter callParameters = callParameter(Wei.ONE, null, null);
    internalAutoSelectIsAllowedExeecdBalance(callParameters, Optional.empty(), false);
  }

  @Test
  public void shouldAutoSelectIsAllowedExeceedingBalanceToFalseWhenGasPriceIsNotZeroAfterEIP1559() {
    JsonCallParameter callParameters = callParameter(Wei.ONE, null, null);
    internalAutoSelectIsAllowedExeecdBalance(callParameters, Optional.of(Wei.ONE), false);
  }

  @Test
  public void shouldAutoSelectIsAllowedExeceedingBalanceToTrueWhenFeesAreZero() {
    JsonCallParameter callParameters = callParameter(null, Wei.ZERO, Wei.ZERO);
    internalAutoSelectIsAllowedExeecdBalance(callParameters, Optional.of(Wei.ONE), true);
  }

  @Test
  public void shouldAutoSelectIsAllowedExeceedingBalanceToFalseWhenFeesAreZero() {
    JsonCallParameter callParameters = callParameter(null, Wei.ONE, Wei.ONE);
    internalAutoSelectIsAllowedExeecdBalance(callParameters, Optional.of(Wei.ONE), false);
  }

  private void internalAutoSelectIsAllowedExeecdBalance(
      final JsonCallParameter callParameter,
      final Optional<Wei> baseFee,
      final boolean isAllowedExeedingBalance) {
    final JsonRpcRequestContext request = ethCallRequest(callParameter, "latest");

    BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(baseFee);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchainQueries.getBlockchain().getChainHead()).thenReturn(chainHead);
    when(blockchainQueries.getBlockchain().getChainHead().getHash()).thenReturn(Hash.ZERO);
    when(blockchainQueries.getBlockHeaderByHash(Hash.ZERO)).thenReturn(Optional.of(blockHeader));

    method.response(request);

    TransactionValidationParams transactionValidationParams =
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator())
            .isAllowExceedingBalance(isAllowedExeedingBalance)
            .build();

    verify(transactionSimulator).process(any(), eq(transactionValidationParams), any(), any());
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
        null);
  }

  private JsonRpcRequestContext ethCallRequest(
      final CallParameter callParameter, final String blockNumberInHex) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eth_call", new Object[] {callParameter, blockNumberInHex}));
  }

  private void mockTransactionProcessorSuccessResult(final Bytes output) {
    final TransactionSimulatorResult result = mock(TransactionSimulatorResult.class);

    when(result.isSuccessful()).thenReturn(true);
    when(result.getValidationResult()).thenReturn(ValidationResult.valid());
    when(result.getOutput()).thenReturn(output);
    when(transactionSimulator.process(any(), any(), any(), any())).thenReturn(Optional.of(result));
  }
}
