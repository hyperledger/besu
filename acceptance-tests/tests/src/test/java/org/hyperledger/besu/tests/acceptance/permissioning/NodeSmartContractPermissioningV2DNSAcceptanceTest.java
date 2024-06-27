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
package org.hyperledger.besu.tests.acceptance.permissioning;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("test is flaky #7191")
public class NodeSmartContractPermissioningV2DNSAcceptanceTest
    extends NodeSmartContractPermissioningV2AcceptanceTestBase {

  private Node bootnode;
  private Node permissionedNode;
  private Node allowedNode;
  private Node forbiddenNode;

  final ImmutableEnodeDnsConfiguration enodeDnsConfiguration =
      ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(true).build();

  @BeforeEach
  public void setUp() {
    bootnode = bootnode("bootnode");
    forbiddenNode = node("forbidden-node");
    allowedNode = node("allowed-node");
    permissionedNode = permissionedNode("permissioned-node");

    permissionedCluster.start(bootnode, forbiddenNode, allowedNode, permissionedNode);

    // updating permissioning smart contract with allowed nodes

    permissionedNode.execute(allowNode(bootnode));
    permissionedNode.verify(connectionIsAllowed(bootnode));

    permissionedNode.execute(allowNode(allowedNode));
    permissionedNode.verify(connectionIsAllowed(allowedNode));

    permissionedNode.execute(allowNode(permissionedNode));
    permissionedNode.verify(connectionIsAllowed(permissionedNode));
  }

  @Test
  public void permissionedNodeShouldAddDnsRuleAndAllowNode() throws UnknownHostException {
    final EnodeURL forbiddenEnodeURL = getForbiddenEnodeURL();
    Assertions.assertThat(forbiddenEnodeURL.toURI().getHost()).isEqualTo("127.0.0.1");
    final EnodeURL forbiddenDnsEnodeURL = buildDnsEnodeUrl(forbiddenEnodeURL);
    Assertions.assertThat(forbiddenDnsEnodeURL.toURI().getHost())
        .isEqualTo(InetAddress.getLocalHost().getHostName());

    permissionedNode.verify(connectionIsForbidden(forbiddenNode));
    permissionedNode.verify(connectionIsForbidden(forbiddenDnsEnodeURL));
    permissionedNode.execute(allowNode(forbiddenDnsEnodeURL));
    permissionedNode.verify(connectionIsAllowed(forbiddenDnsEnodeURL));
    permissionedNode.execute(forbidNode(forbiddenEnodeURL));
  }

  @Test
  public void permissionedNodeShouldAddDNSRuleAndConnectToNewPeer() throws UnknownHostException {
    final EnodeURL forbiddenEnodeURL = getForbiddenEnodeURL();
    Assertions.assertThat(forbiddenEnodeURL.toURI().getHost()).isEqualTo("127.0.0.1");
    final EnodeURL forbiddenDnsEnodeURL = buildDnsEnodeUrl(forbiddenEnodeURL);
    Assertions.assertThat(forbiddenDnsEnodeURL.toURI().getHost())
        .isEqualTo(InetAddress.getLocalHost().getHostName());

    permissionedNode.verify(net.awaitPeerCount(2));
    permissionedNode.verify(connectionIsForbidden(forbiddenNode));
    permissionedNode.verify(connectionIsForbidden(forbiddenDnsEnodeURL));
    permissionedNode.execute(allowNode(forbiddenDnsEnodeURL));
    permissionedNode.verify(connectionIsAllowed(forbiddenDnsEnodeURL));
    permissionedNode.verify(admin.addPeer(forbiddenNode));
    permissionedNode.verify(net.awaitPeerCount(3));
    permissionedNode.execute(forbidNode(forbiddenEnodeURL));
  }

  private EnodeURL getForbiddenEnodeURL() {
    return EnodeURLImpl.fromURI(((RunnableNode) forbiddenNode).enodeUrl());
  }

  private EnodeURL buildDnsEnodeUrl(final EnodeURL forbiddenEnodeURL) {
    return EnodeURLImpl.builder()
        .configureFromEnode(forbiddenEnodeURL)
        .ipAddress("localhost", enodeDnsConfiguration)
        .build();
  }
}
