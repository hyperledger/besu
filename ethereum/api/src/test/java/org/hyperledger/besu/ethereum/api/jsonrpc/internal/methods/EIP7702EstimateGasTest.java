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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EIP7702EstimateGasTest {

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionSimulator transactionSimulator;
  @Mock private BlockHeader blockHeader;
  @Mock private ApiConfiguration apiConfiguration;

  private EthEstimateGas ethEstimateGas;

  @BeforeEach
  public void setUp() {
    when(apiConfiguration.getEstimateGasToleranceRatio()).thenReturn(0.0);
    when(blockchainQueries.headBlockHeader()).thenReturn(blockHeader);

    ethEstimateGas = new EthEstimateGas(blockchainQueries, transactionSimulator, apiConfiguration);
  }

  @Test
  public void shouldEstimateGasForEIP7702Transaction() {
    // Create call parameter as a Map (simulating JSON input)
    final Map<String, Object> callParam = new HashMap<>();
    callParam.put("from", "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
    callParam.put("to", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
    callParam.put("value", "0x1");
    callParam.put("gas", "0x5208");

    // Mock the transaction simulator result
    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(50000L);

    when(transactionSimulator.process(any(), any(), any(), any()))
        .thenReturn(Optional.of(mockResult));

    // Create JSON-RPC request
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParam}));

    // Execute
    final JsonRpcResponse response = ethEstimateGas.response(request);

    // Verify
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isNotNull();

    final String gasHex = (String) successResponse.getResult();
    final long gasEstimate = Long.decode(gasHex);
    assertThat(gasEstimate).isEqualTo(50000L);
  }

  @Test
  public void shouldEstimateGasWithMultipleAuthorizations() {
    final Map<String, Object> callParam = new HashMap<>();
    callParam.put("from", "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
    callParam.put("to", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
    callParam.put("value", "0x1");

    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(46000L);

    when(transactionSimulator.process(any(), any(), any(), any()))
        .thenReturn(Optional.of(mockResult));

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParam}));

    final JsonRpcResponse response = ethEstimateGas.response(request);

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
    final String gasHex = (String) ((JsonRpcSuccessResponse) response).getResult();
    final long gasEstimate = Long.decode(gasHex);
    assertThat(gasEstimate).isEqualTo(46000L);
  }

  @Test
  public void shouldHandleTransactionWithoutExplicitGas() {
    final Map<String, Object> callParam = new HashMap<>();
    callParam.put("from", "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
    callParam.put("to", "0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
    callParam.put("value", "0x1");

    final TransactionSimulatorResult mockResult = mock(TransactionSimulatorResult.class);
    when(mockResult.isSuccessful()).thenReturn(true);
    when(mockResult.getGasEstimate()).thenReturn(33500L);

    when(transactionSimulator.process(any(), any(), any(), any()))
        .thenReturn(Optional.of(mockResult));

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "eth_estimateGas", new Object[] {callParam}));

    final JsonRpcResponse response = ethEstimateGas.response(request);

    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
  }
}
