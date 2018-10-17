/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.CallParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessingResult;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessor;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

import java.util.Optional;

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
  @Mock private TransientTransactionProcessor transientTransactionProcessor;

  @Before
  public void setUp() {
    when(blockchainQueries.headBlockNumber()).thenReturn(1L);
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getBlockHeader(eq(1L))).thenReturn(Optional.of(blockHeader));
    when(blockHeader.getGasLimit()).thenReturn(Long.MAX_VALUE);
    when(blockHeader.getNumber()).thenReturn(1L);

    method =
        new EthEstimateGas(
            blockchainQueries, transientTransactionProcessor, new JsonRpcParameter());
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("eth_estimateGas");
  }

  @Test
  public void shouldReturnErrorWhenTransientTransactionProcessorReturnsEmpty() {
    final JsonRpcRequest request = ethEstimateGasRequest(callParameter());
    when(transientTransactionProcessor.process(eq(modifiedCallParameter()), eq(1L)))
        .thenReturn(Optional.empty());

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.INTERNAL_ERROR);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnGasEstimateWhenTransientTransactionProcessorReturnsResult() {
    final JsonRpcRequest request = ethEstimateGasRequest(callParameter());
    mockTransientProcessorResultGasEstimate(1L);

    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, Quantity.create(1L));

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  private void mockTransientProcessorResultGasEstimate(final long gasEstimate) {
    final TransientTransactionProcessingResult result =
        mock(TransientTransactionProcessingResult.class);
    when(result.getGasEstimate()).thenReturn(gasEstimate);
    when(transientTransactionProcessor.process(eq(modifiedCallParameter()), eq(1L)))
        .thenReturn(Optional.of(result));
  }

  private CallParameter callParameter() {
    return new CallParameter("0x0", "0x0", "0x0", "0x0", "0x0", "");
  }

  private CallParameter modifiedCallParameter() {
    return new CallParameter("0x0", "0x0", Quantity.create(Long.MAX_VALUE), "0x0", "0x0", "");
  }

  private JsonRpcRequest ethEstimateGasRequest(final CallParameter callParameter) {
    return new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParameter});
  }
}
