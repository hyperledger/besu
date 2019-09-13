/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;

import org.junit.Before;
import org.junit.Test;

public class EthSendTransactionTest {

  private JsonRpcMethod method;

  @Before
  public void before() {
    method = new EthSendTransaction();
  }

  @Test
  public void methodReturnExpectedName() {
    assertThat(method.getName()).isEqualTo("eth_sendTransaction");
  }

  @Test
  public void requestIsMissingParameter() {
    final JsonRpcRequest request =
        new JsonRpcRequest("2.0", "eth_sendTransaction", new String[] {});

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.ETH_SEND_TX_NOT_AVAILABLE);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }
}
