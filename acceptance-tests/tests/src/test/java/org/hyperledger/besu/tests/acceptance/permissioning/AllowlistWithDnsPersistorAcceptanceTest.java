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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor.ALLOWLIST_TYPE;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AllowlistWithDnsPersistorAcceptanceTest extends AcceptanceTestBase {

  public static final String ENODE_PREFIX =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@";
  public static final String PORT_SUFFIX = ":4567";

  private String ENODE_LOCALHOST_DNS;
  private String ENODE_LOCALHOST_IP;
  private String ENODE_TWO_IP;

  private Node node;
  private Account senderA;
  private Path tempFile;

  @BeforeEach
  public void setUp() throws Exception {
    ENODE_LOCALHOST_DNS = ENODE_PREFIX + InetAddress.getLocalHost().getHostName() + PORT_SUFFIX;
    ENODE_LOCALHOST_IP = ENODE_PREFIX + "127.0.0.1" + PORT_SUFFIX;
    ENODE_TWO_IP =
        "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:1234";

    senderA = accounts.getPrimaryBenefactor();
    tempFile = Files.createTempFile("test", "perm-dns-test");

    this.node =
        permissionedNodeBuilder
            .name("node")
            .nodesConfigFile(tempFile)
            .nodesPermittedInConfig(new ArrayList<>())
            .accountsConfigFile(tempFile)
            .accountsPermittedInConfig(Collections.singletonList(senderA.getAddress()))
            .dnsEnabled(true)
            .build();

    cluster.start(this.node);
  }

  @Test
  public void addingEnodeWithIp_andThenAddingSameEnodeWithHostname_shouldThrow() {

    node.verify(perm.addNodesToAllowlist(ENODE_LOCALHOST_IP));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_LOCALHOST_DNS));

    // expect an exception when adding using hostname, since this node is already added with IP
    final Condition condition = perm.addNodesToAllowlist(ENODE_LOCALHOST_DNS);
    assertThatThrownBy(() -> node.verify(condition)).isInstanceOf(RuntimeException.class);
  }

  @Test
  public void addingEnodeWithHostname_andThenAddingSameEnodeWithIp_shouldThrow() {

    node.verify(perm.addNodesToAllowlist(ENODE_LOCALHOST_DNS));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_LOCALHOST_DNS));

    // expect an exception when adding using IP, since this node is already added with hostname
    final Condition condition = perm.addNodesToAllowlist(ENODE_LOCALHOST_IP);
    assertThatThrownBy(() -> node.verify(condition)).isInstanceOf(RuntimeException.class);
  }

  @Test
  public void addingEnodeWithHostNameShouldWorkWhenDnsEnabled() {

    node.verify(perm.addNodesToAllowlist(ENODE_LOCALHOST_DNS));

    // This should just work since there is no IP address to resolve to a host name.
    // With DNS enabled, the ENODE with the DNS hostname in it should remain as is.
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_LOCALHOST_DNS));
  }

  @Test
  public void manipulatedNodesAllowlistWithHostnameShouldWorkWhenDnsEnabled() {

    node.verify(perm.addNodesToAllowlist(ENODE_LOCALHOST_DNS, ENODE_TWO_IP));
    // use DNS config to resolve the Enode with IP. It either resolves to a hostname or remain as is
    final EnodeURL enodeURL0 =
        EnodeURLImpl.fromString(
            ENODE_TWO_IP,
            ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(true).build());
    final String enode2ResolvedToDns = enodeURL0.toString();
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_LOCALHOST_DNS, enode2ResolvedToDns));

    node.verify(perm.removeNodesFromAllowlist(ENODE_LOCALHOST_DNS));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, enode2ResolvedToDns));
  }
}
