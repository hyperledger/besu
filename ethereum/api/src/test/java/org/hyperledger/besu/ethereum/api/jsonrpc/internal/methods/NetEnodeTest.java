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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NetEnodeTest {

  private NetEnode method;

  private final Bytes nodeId =
      Bytes.fromHexString(
          "0x0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");

  private final DefaultPeer defaultPeer =
      DefaultPeer.fromEnodeURL(
          EnodeURLImpl.builder()
              .nodeId(nodeId)
              .ipAddress("1.2.3.4")
              .discoveryPort(7890)
              .listeningPort(30303)
              .build());

  private final Optional<EnodeURL> enodeURL = Optional.of(defaultPeer.getEnodeURL());

  @Mock private P2PNetwork p2PNetwork;

  @BeforeEach
  public void before() {
    this.method = new NetEnode(p2PNetwork);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("net_enode");
  }

  @Test
  public void shouldReturnEnode() {
    when(p2PNetwork.isP2pEnabled()).thenReturn(true);
    doReturn(enodeURL).when(p2PNetwork).getLocalEnode();

    final JsonRpcRequestContext request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), enodeURL.get().toString());

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenP2pDisabled() {
    when(p2PNetwork.isP2pEnabled()).thenReturn(false);

    final JsonRpcRequestContext request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.P2P_DISABLED);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenP2PEnabledButNoEnodeFound() {
    when(p2PNetwork.isP2pEnabled()).thenReturn(true);
    doReturn(Optional.empty()).when(p2PNetwork).getLocalEnode();

    final JsonRpcRequestContext request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.P2P_NETWORK_NOT_RUNNING);

    Assertions.assertThat(method.response(request))
        .usingRecursiveComparison()
        .isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext netEnodeRequest() {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "net_enode", new Object[] {}));
  }
}
