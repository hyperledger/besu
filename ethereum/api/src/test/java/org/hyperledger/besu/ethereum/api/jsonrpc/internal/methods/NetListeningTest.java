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
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NetListeningTest {

  private NetListening method;

  @Mock private P2PNetwork p2PNetwork;

  @BeforeEach
  public void before() {
    this.method = new NetListening(p2PNetwork);
  }

  @Test
  public void shouldReturnTrueWhenNetworkIsListening() {
    when(p2PNetwork.isListening()).thenReturn(true);

    final JsonRpcRequestContext request = netListeningRequest();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnFalseWhenNetworkIsNotListening() {
    when(p2PNetwork.isListening()).thenReturn(false);

    final JsonRpcRequestContext request = netListeningRequest();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, false);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void getPermissions() {
    List<String> permissions = method.getPermissions();
    assertThat(permissions).containsExactly("*:*", "net:*", "net:listening");
  }

  private JsonRpcRequestContext netListeningRequest() {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "net_listening", new Object[] {}));
  }
}
