/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions.Action;

import java.util.List;

import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.junit.jupiter.api.Test;

public class PeerPermissionsSubnetTest {

  private final Peer remoteNode = createPeer();

  @Test
  public void peerInSubnetRangeShouldBePermitted() {
    List<SubnetInfo> allowedSubnets = List.of(subnet("127.0.0.0/24"));
    PeerPermissionSubnet peerPermissionSubnet = new PeerPermissionSubnet(allowedSubnets);
    checkPermissions(peerPermissionSubnet, remoteNode, true);
  }

  @Test
  public void peerInAtLeastOneSubnetRangeShouldBePermitted() {
    List<SubnetInfo> allowedSubnets = List.of(subnet("127.0.0.0/24"), subnet("10.0.0.1/24"));
    PeerPermissionSubnet peerPermissionSubnet = new PeerPermissionSubnet(allowedSubnets);
    checkPermissions(peerPermissionSubnet, remoteNode, true);
  }

  @Test
  public void peerOutSubnetRangeShouldNotBePermitted() {
    List<SubnetInfo> allowedSubnets = List.of(subnet("10.0.0.0/24"));
    PeerPermissionSubnet peerPermissionSubnet = new PeerPermissionSubnet(allowedSubnets);
    checkPermissions(peerPermissionSubnet, remoteNode, false);
  }

  @Test
  public void peerShouldBePermittedIfNoSubnets() {
    PeerPermissionSubnet peerPermissionSubnet = new PeerPermissionSubnet(List.of());
    checkPermissions(peerPermissionSubnet, remoteNode, true);
  }

  private void checkPermissions(
      final PeerPermissions peerPermissions, final Peer remotePeer, final boolean expectedResult) {
    for (Action action : Action.values()) {
      assertThat(peerPermissions.isPermitted(createPeer(), remotePeer, action))
          .isEqualTo(expectedResult);
    }
  }

  private SubnetInfo subnet(final String subnet) {
    return new SubnetUtils(subnet).getInfo();
  }

  private Peer createPeer() {
    return DefaultPeer.fromEnodeURL(
        EnodeURLImpl.builder()
            .nodeId(Peer.randomId())
            .ipAddress("127.0.0.1")
            .discoveryAndListeningPorts(EnodeURLImpl.DEFAULT_LISTENING_PORT)
            .build());
  }
}
