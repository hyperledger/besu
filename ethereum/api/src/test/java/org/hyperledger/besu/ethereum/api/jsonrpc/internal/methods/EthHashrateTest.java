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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthHashrateTest {

  @Mock private PoWMiningCoordinator miningCoordinator;
  private EthHashrate method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_hashrate";

  @Before
  public void setUp() {
    method = new EthHashrate(miningCoordinator);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnValueFromMiningCoordinator() {
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), "0xc");
    when(miningCoordinator.hashesPerSecond()).thenReturn(Optional.of(12L));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnZeroWhenMiningCoordinatorDoesNotHaveHashes() {
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), "0x0");
    when(miningCoordinator.hashesPerSecond()).thenReturn(Optional.empty());

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext requestWithParams() {
    return new JsonRpcRequestContext(
        new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, new Object[] {}));
  }
}
