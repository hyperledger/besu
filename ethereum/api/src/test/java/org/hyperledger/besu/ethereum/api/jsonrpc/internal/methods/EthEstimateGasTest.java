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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.ethereum.vm.OperationTracer;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthEstimateGasTest {

  private EthEstimateGas method;

  @Mock private BlockHeader blockHeader;
  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionSimulator transactionSimulator;

  @Before
  public void setUp() {
    when(blockchainQueries.headBlockNumber()).thenReturn(1L);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getBlockHeader(eq(1L))).thenReturn(Optional.of(blockHeader));
    when(blockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(blockHeader.getNumber()).thenReturn(1L);

    method = new EthEstimateGas(blockchainQueries, transactionSimulator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("eth_estimateGas");
  }

  @Test
  public void shouldReturnErrorWhenTransientTransactionProcessorReturnsEmpty() {
    final JsonRpcRequestContext request = ethEstimateGasRequest(callParameter());
    when(transactionSimulator.process(
            eq(modifiedCallParameter()), any(OperationTracer.class), eq(1L)))
        .thenReturn(Optional.empty());

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INTERNAL_ERROR);

    Assertions.assertThat(method.response(request))
        .isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnGasEstimateWhenTransientTransactionProcessorReturnsResultSuccess() {
    final JsonRpcRequestContext request = ethEstimateGasRequest(callParameter());
    mockTransientProcessorResultGasEstimate(1L, true);

    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, Quantity.create(1L));

    Assertions.assertThat(method.response(request))
        .isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnGasEstimateErrorWhenTransientTransactionProcessorReturnsResultFailure() {
    final JsonRpcRequestContext request = ethEstimateGasRequest(callParameter());
    mockTransientProcessorResultGasEstimate(1L, false);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INTERNAL_ERROR);

    Assertions.assertThat(method.response(request))
        .isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenTransactionProcessorReturnsTxInvalidReason() {
    final JsonRpcRequestContext request = ethEstimateGasRequest(callParameter());
    mockTransientProcessorResultTxInvalidReason(
        TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.TRANSACTION_UPFRONT_COST_EXCEEDS_BALANCE);

    Assertions.assertThat(method.response(request))
        .isEqualToComparingFieldByField(expectedResponse);
  }

  private void mockTransientProcessorResultTxInvalidReason(final TransactionInvalidReason reason) {
    final TransactionSimulatorResult mockTxSimResult = getMockTransactionSimulatorResult(false, 0);
    when(mockTxSimResult.getValidationResult()).thenReturn(ValidationResult.invalid(reason));
  }

  private void mockTransientProcessorResultGasEstimate(
      final long estimateGas, final boolean isSuccessful) {
    getMockTransactionSimulatorResult(isSuccessful, estimateGas);
  }

  private TransactionSimulatorResult getMockTransactionSimulatorResult(
      final boolean isSuccessful, final long estimateGas) {
    final TransactionSimulatorResult mockTxSimResult = mock(TransactionSimulatorResult.class);
    when(transactionSimulator.process(
            eq(modifiedCallParameter()), any(OperationTracer.class), eq(1L)))
        .thenReturn(Optional.of(mockTxSimResult));
    final TransactionProcessor.Result mockResult = mock(TransactionProcessor.Result.class);
    when(mockResult.getEstimateGasUsedByTransaction()).thenReturn(estimateGas);
    when(mockTxSimResult.getResult()).thenReturn(mockResult);
    when(mockTxSimResult.isSuccessful()).thenReturn(isSuccessful);
    return mockTxSimResult;
  }

  private JsonCallParameter callParameter() {
    return new JsonCallParameter("0x0", "0x0", "0x0", "0x0", "0x0", "");
  }

  private JsonCallParameter modifiedCallParameter() {
    return new JsonCallParameter("0x0", "0x0", Quantity.create(Long.MAX_VALUE), "0x0", "0x0", "");
  }

  private JsonRpcRequestContext ethEstimateGasRequest(final CallParameter callParameter) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParameter}));
  }
}
