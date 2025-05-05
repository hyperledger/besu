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
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.BLOCK_NOT_FOUND;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.SimulateV1Parameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.transaction.PreCloseStateHandler;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;

import java.util.List;
import java.util.Optional;

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
public class EthSimulateV1Test {

  private EthSimulateV1 method;

  @Mock private BlockchainQueries blockchainQueries;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private TransactionSimulator transactionSimulator;
  @Mock private MiningConfiguration miningConfiguration;
  @Mock private ApiConfiguration apiConfiguration;

  @Captor ArgumentCaptor<PreCloseStateHandler<Optional<JsonRpcResponse>>> mapperCaptor;

  @BeforeEach
  public void setUp() {
    method =
        new EthSimulateV1(
            blockchainQueries,
            protocolSchedule,
            transactionSimulator,
            miningConfiguration,
            apiConfiguration);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("eth_simulateV1");
  }

  @Test
  public void shouldReturnBlockNotFoundWhenInvalidBlockNumberSpecified() {
    final JsonRpcRequestContext request =
        ethSimulateV1Request(simulateParameter(), Quantity.create(33L));
    when(blockchainQueries.headBlockNumber()).thenReturn(14L);
    final JsonRpcResponse expectedResponse = new JsonRpcErrorResponse(null, BLOCK_NOT_FOUND);

    final JsonRpcResponse response = method.response(request);
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(blockchainQueries).headBlockNumber();
  }

  private SimulateV1Parameter simulateParameter() {
    return new SimulateV1Parameter(List.of(), false, false, false);
  }

  private JsonRpcRequestContext ethSimulateV1Request(
      final SimulateV1Parameter simulateV1Parameter, final String blockNumberInHex) {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(
            "2.0", "eth_simulateV1", new Object[] {simulateV1Parameter, blockNumberInHex}));
  }
}
