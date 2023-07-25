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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.blockcreation.CoinbaseNotSetException;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MinerStartTest {

  private MinerStart method;

  @Mock private PoWMiningCoordinator miningCoordinator;

  @BeforeEach
  public void before() {
    method = new MinerStart(miningCoordinator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("miner_start");
  }

  @Test
  public void shouldReturnTrueWhenMiningStartsSuccessfully() {
    when(miningCoordinator.enable()).thenReturn(true);
    final JsonRpcRequestContext request = minerStart();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnFalseWhenMiningDoesNotStart() {
    when(miningCoordinator.enable()).thenReturn(false);
    final JsonRpcRequestContext request = minerStart();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, false);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnCoinbaseNotSetErrorWhenCoinbaseHasNotBeenSet() {
    final JsonRpcRequestContext request = minerStart();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, RpcErrorType.COINBASE_NOT_SET);

    doThrow(new CoinbaseNotSetException("")).when(miningCoordinator).enable();

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext minerStart() {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "miner_start", null));
  }
}
