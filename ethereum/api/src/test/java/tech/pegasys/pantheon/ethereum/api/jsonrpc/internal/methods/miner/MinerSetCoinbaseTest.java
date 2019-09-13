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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Address;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MinerSetCoinbaseTest {

  private MinerSetCoinbase method;

  @Mock private MiningCoordinator miningCoordinator;

  @Before
  public void before() {
    this.method = new MinerSetCoinbase(miningCoordinator, new JsonRpcParameter());
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("miner_setCoinbase");
  }

  @Test
  public void shouldFailWhenMissingAddress() {
    final JsonRpcRequest request = minerSetCoinbaseRequest(null);

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void shouldFailWhenAddressIsInvalid() {
    final JsonRpcRequest request = minerSetCoinbaseRequest("foo");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown).isInstanceOf(InvalidJsonRpcParameters.class);
  }

  @Test
  public void shouldSetCoinbaseWhenRequestHasAddress() {
    final JsonRpcRequest request = minerSetCoinbaseRequest("0x0");
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse response = method.response(request);

    verify(miningCoordinator).setCoinbase(eq(Address.fromHexString("0x0")));
    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnAnInvalidRequestIfUnderlyingOperationThrowsUnsupportedOperation() {
    final JsonRpcRequest request = minerSetCoinbaseRequest("0x0");
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);

    doAnswer(
            invocation -> {
              throw new UnsupportedOperationException();
            })
        .when(miningCoordinator)
        .setCoinbase(any());

    final JsonRpcResponse response = method.response(request);
    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest minerSetCoinbaseRequest(final String hexString) {
    if (hexString != null) {
      return new JsonRpcRequest("2.0", "miner_setCoinbase", new Object[] {hexString});
    } else {
      return new JsonRpcRequest("2.0", "miner_setCoinbase", new Object[] {});
    }
  }
}
