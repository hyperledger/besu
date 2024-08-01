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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MinerSetCoinbaseTest {

  private MinerSetCoinbase method;

  @Mock private MiningCoordinator miningCoordinator;

  @BeforeEach
  public void before() {
    this.method = new MinerSetCoinbase(miningCoordinator);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("miner_setCoinbase");
  }

  @Test
  public void shouldFailWhenMissingAddress() {
    final JsonRpcRequestContext request = minerSetCoinbaseRequest(null);

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid address parameter (index 0)");
  }

  @Test
  public void shouldFailWhenAddressIsInvalid() {
    final JsonRpcRequestContext request = minerSetCoinbaseRequest("foo");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown).isInstanceOf(InvalidJsonRpcParameters.class);
  }

  @Test
  public void shouldSetCoinbaseWhenRequestHasAddress() {
    final JsonRpcRequestContext request = minerSetCoinbaseRequest("0x0");
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse response = method.response(request);

    verify(miningCoordinator).setCoinbase(eq(Address.fromHexString("0x0")));
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnAnInvalidRequestIfUnderlyingOperationThrowsUnsupportedOperation() {
    final JsonRpcRequestContext request = minerSetCoinbaseRequest("0x0");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.INVALID_REQUEST);

    doAnswer(
            invocation -> {
              throw new UnsupportedOperationException();
            })
        .when(miningCoordinator)
        .setCoinbase(any());

    final JsonRpcResponse response = method.response(request);
    assertThat(response).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext minerSetCoinbaseRequest(final String hexString) {
    if (hexString != null) {
      return new JsonRpcRequestContext(
          new JsonRpcRequest("2.0", "miner_setCoinbase", new Object[] {hexString}));
    } else {
      return new JsonRpcRequestContext(
          new JsonRpcRequest("2.0", "miner_setCoinbase", new Object[] {}));
    }
  }
}
