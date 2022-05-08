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

public class AllowlistWithDnsPersistorAcceptanceTest extends AcceptanceTestBase {

  private String ENODE_ONE;
  private String ENODE_TWO;
  private String ENODE_THREE;

  private Node node;
  private Account senderA;
  private Path tempFile;

  @Before
  public void setUp() throws Exception {
    ENODE_ONE =
        "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@"
            + InetAddress.getLocalHost().getHostName()
            + ":4567";
    ENODE_TWO =
        "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
    ENODE_THREE =
        "enode://4f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.11:4567";

    senderA = accounts.getPrimaryBenefactor();
    tempFile = Files.createTempFile("test", "test");

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

  @Ignore ("test is failing in CI")
  @Test
  public void manipulatedNodesAllowlistWithHostnameShouldWorkWhenDnsEnabled() {

    node.verify(perm.addNodesToAllowlist(ENODE_ONE, ENODE_TWO));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_ONE, ENODE_TWO));

    node.verify(perm.removeNodesFromAllowlist(ENODE_ONE));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(ALLOWLIST_TYPE.NODES, tempFile, ENODE_TWO));

    node.verify(perm.addNodesToAllowlist(ENODE_ONE, ENODE_THREE));
    node.verify(
        perm.expectPermissioningAllowlistFileKeyValue(
            ALLOWLIST_TYPE.NODES, tempFile, ENODE_TWO, ENODE_ONE, ENODE_THREE));
  }
}
