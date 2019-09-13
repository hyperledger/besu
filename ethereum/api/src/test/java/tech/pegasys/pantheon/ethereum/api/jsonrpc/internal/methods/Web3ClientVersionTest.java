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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Test;

public class Web3ClientVersionTest {

  private final String CLIENT_VERSION = "pantheon/1.0.0";
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "web3_clientVersion";

  private final Web3ClientVersion method = new Web3ClientVersion(CLIENT_VERSION);

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnCorrectResult() {
    final JsonRpcRequest request =
        new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, new Object[] {});

    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getId(), CLIENT_VERSION);
    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }
}
