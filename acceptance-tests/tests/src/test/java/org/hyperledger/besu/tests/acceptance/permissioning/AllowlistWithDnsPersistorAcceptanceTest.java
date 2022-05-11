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

import static org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor.ALLOWLIST_TYPE;

import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllowlistWithDnsPersistorAcceptanceTest extends AcceptanceTestBase {
  private static final Logger LOG =
      LoggerFactory.getLogger(AllowlistWithDnsPersistorAcceptanceTest.class);

  private String ENODE_ONE_DNS;
  private String ENODE_TWO_IP;
  private String ENODE_THREE_IP;

  private Node node;
  private Account senderA;
  private Path tempFile;

  @Before
  public void setUp() throws Exception {
    ENODE_ONE_DNS =
        "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@"
            + InetAddress.getLocalHost().getHostName()
            + ":4567";
    ENODE_TWO_IP =
        "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:1234";
    ENODE_THREE_IP =
        "enode://4f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.11:3456";

    senderA = accounts.getPrimaryBenefactor();
    tempFile = Files.createTempFile("test", "perm-dns-test0");

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

  @Ignore("test is failing in CI")
  @Test
  public void manipulatedNodesAllowlistWithHostnameShouldWorkWhenDnsEnabled() {

    LOG.info("temp file " + tempFile.toAbsolutePath());

    node.verify(perm.addNodesToAllowlist(ENODE_ONE_DNS, ENODE_TWO_IP));
    LOG.info("enode one " + ENODE_ONE_DNS);
    LOG.info("enode two " + ENODE_TWO_IP);
    final EnodeURL enodeURL1 =
        EnodeURLImpl.fromString(
            ENODE_ONE_DNS,
            ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(false).build());
    LOG.info("enode from 1 string with DNS enabled but NOT update " + enodeURL1);
    final EnodeURL enodeURL0 =
        EnodeURLImpl.fromString(
            ENODE_ONE_DNS,
            ImmutableEnodeDnsConfiguration.builder().dnsEnabled(true).updateEnabled(true).build());
    LOG.info("enode from 1 string with DNS enabled AND update " + enodeURL0);
    final EnodeURL enodeURL2 =
        EnodeURLImpl.fromString(
            ENODE_TWO_IP,
            ImmutableEnodeDnsConfiguration.builder()
                .dnsEnabled(false)
                .updateEnabled(false)
                .build());
    LOG.info("enode from 2 string " + enodeURL2);
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_ONE_DNS, ENODE_TWO_IP));

    node.verify(perm.removeNodesFromAllowlist(ENODE_ONE_DNS));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_TWO_IP));

    node.verify(perm.addNodesToAllowlist(ENODE_ONE_DNS, ENODE_THREE_IP));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_TWO_IP, ENODE_ONE_DNS, ENODE_THREE_IP));

    node.verify(perm.removeNodesFromAllowlist(ENODE_TWO_IP));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_ONE_DNS, ENODE_THREE_IP));
  }
}
