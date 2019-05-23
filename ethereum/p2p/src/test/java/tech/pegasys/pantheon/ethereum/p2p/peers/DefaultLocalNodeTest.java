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
package tech.pegasys.pantheon.ethereum.p2p.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;

import tech.pegasys.pantheon.ethereum.p2p.peers.LocalNode.NodeNotReadyException;
import tech.pegasys.pantheon.ethereum.p2p.peers.MutableLocalNode.NodeAlreadySetException;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DefaultLocalNodeTest {
  private final String clientId = "test client";
  private final int p2pVersion = 5;
  private final List<Capability> supportedCapabilities =
      Arrays.asList(Capability.create("eth", 63));
  private final BytesValue nodeId = BytesValue.of(new byte[64]);
  private final int port = 30303;
  private final EnodeURL enode =
      EnodeURL.builder()
          .ipAddress("127.0.0.1")
          .discoveryAndListeningPorts(port)
          .nodeId(nodeId)
          .build();
  private final PeerInfo peerInfo =
      new PeerInfo(p2pVersion, clientId, supportedCapabilities, port, nodeId);
  private final Peer peer = DefaultPeer.fromEnodeURL(enode);

  @Test
  public void create() {
    final LocalNode localNode = createLocalNode();
    assertThat(localNode.isReady()).isFalse();
    assertThatThrownBy(localNode::getPeerInfo).isInstanceOf(NodeNotReadyException.class);
    assertThatThrownBy(localNode::getPeer).isInstanceOf(NodeNotReadyException.class);
  }

  @Test
  public void setEnode() {
    final MutableLocalNode localNode = createLocalNode();
    localNode.setEnode(enode);

    assertThat(localNode.isReady()).isTrue();
    validateReadyNode(localNode);

    // Verify we can't set the enode a second time
    assertThatThrownBy(() -> localNode.setEnode(enode)).isInstanceOf(NodeAlreadySetException.class);
  }

  private MutableLocalNode createLocalNode() {
    return DefaultLocalNode.create(clientId, p2pVersion, supportedCapabilities);
  }

  private void validateReadyNode(final LocalNode localNode) {
    assertThat(localNode.getPeerInfo()).isEqualTo(peerInfo);
    assertThat(localNode.getPeer()).isEqualTo(peer);
  }
}
