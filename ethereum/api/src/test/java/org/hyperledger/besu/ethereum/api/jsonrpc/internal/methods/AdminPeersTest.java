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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.MockPeerConnection;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.PeerResult;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AdminPeersTest {

  private AdminPeers adminPeers;

  @Mock private P2PNetwork p2pNetwork;

  @Before
  public void before() {
    adminPeers = new AdminPeers(p2pNetwork);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(adminPeers.getName()).isEqualTo("admin_peers");
  }

  @Test
  public void shouldReturnEmptyPeersListWhenP2PNetworkDoesNotHavePeers() {
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(null, Collections.emptyList());
    final JsonRpcRequest request = adminPeers();
    when(p2pNetwork.getPeers()).thenReturn(Collections.emptyList());

    final JsonRpcResponse response = adminPeers.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnExpectedPeerListWhenP2PNetworkHavePeers() {
    final Collection<PeerConnection> peerList = peerList();
    final List<PeerResult> expectedPeerResults =
        peerList.stream().map(PeerResult::new).collect(Collectors.toList());

    final JsonRpcRequest request = adminPeers();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, expectedPeerResults);

    when(p2pNetwork.getPeers()).thenReturn(peerList);

    final JsonRpcResponse response = adminPeers.response(request);

    assertThat(response).isEqualToComparingFieldByFieldRecursively(expectedResponse);
  }

  @Test
  public void shouldFailIfP2pDisabled() {
    when(p2pNetwork.getPeers()).thenThrow(new P2PDisabledException("P2P disabled."));

    final JsonRpcRequest request = adminPeers();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_DISABLED);

    Assertions.assertThat(adminPeers.response(request))
        .isEqualToComparingFieldByField(expectedResponse);
  }

  private Collection<PeerConnection> peerList() {
    final PeerInfo peerInfo =
        new PeerInfo(5, "0x0", Collections.emptyList(), 30303, BytesValue.EMPTY);
    final PeerConnection p =
        MockPeerConnection.create(
            peerInfo,
            InetSocketAddress.createUnresolved("1.2.3.4", 9876),
            InetSocketAddress.createUnresolved("4.3.2.1", 6789));
    return Lists.newArrayList(p);
  }

  private JsonRpcRequest adminPeers() {
    return new JsonRpcRequest("2.0", "admin_peers", new Object[] {});
  }
}
