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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.EnodeURL;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetEnodeTest {

  private NetEnode method;

  private final BytesValue nodeId =
      BytesValue.fromHexString(
          "0x0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");

  private final DefaultPeer defaultPeer =
      DefaultPeer.fromEnodeURL(
          EnodeURL.builder()
              .nodeId(nodeId)
              .ipAddress("1.2.3.4")
              .discoveryPort(7890)
              .listeningPort(30303)
              .build());

  private final Optional<EnodeURL> enodeURL = Optional.of(defaultPeer.getEnodeURL());

  @Mock private P2PNetwork p2PNetwork;

  @Before
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

    final JsonRpcRequest request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), enodeURL.get().toString());

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenP2pDisabled() {
    when(p2PNetwork.isP2pEnabled()).thenReturn(false);

    final JsonRpcRequest request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_DISABLED);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenP2PEnabledButNoEnodeFound() {
    when(p2PNetwork.isP2pEnabled()).thenReturn(true);
    doReturn(Optional.empty()).when(p2PNetwork).getLocalEnode();

    final JsonRpcRequest request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_NETWORK_NOT_RUNNING);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest netEnodeRequest() {
    return new JsonRpcRequest("2.0", "net_enode", new Object[] {});
  }
}
