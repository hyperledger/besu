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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.DefaultPeer;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetEnodeTest {

  private static final String TESTED_METHOD_NAME = "net_enode";

  private NetEnode method;

  private final BytesValue nodeId =
      BytesValue.fromHexString(
          "0x0f1b319e32017c3fcb221841f0f978701b4e9513fe6a567a2db43d43381a9c7e3dfe7cae13cbc2f56943400bacaf9082576ab087cd51983b17d729ae796f6807");

  private final DefaultPeer defaultPeer = new DefaultPeer(nodeId, "1.2.3.4", 7890, 30303);
  private final Optional<Peer> advertisedPeer = Optional.of(defaultPeer);

  @Mock private P2PNetwork p2PNetwork;

  @Before
  public void before() {
    this.method = new NetEnode(p2PNetwork);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo(TESTED_METHOD_NAME);
  }

  @Test
  public void shouldReturnEnode() {
    when(p2PNetwork.isP2pEnabled()).thenReturn(true);
    doReturn(advertisedPeer).when(p2PNetwork).getAdvertisedPeer();

    final JsonRpcRequest request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), advertisedPeer.get().getEnodeURLString());

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
    doReturn(Optional.empty()).when(p2PNetwork).getAdvertisedPeer();

    final JsonRpcRequest request = netEnodeRequest();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_DISABLED);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest netEnodeRequest() {
    return new JsonRpcRequest("2.0", TESTED_METHOD_NAME, new Object[] {});
  }
}
