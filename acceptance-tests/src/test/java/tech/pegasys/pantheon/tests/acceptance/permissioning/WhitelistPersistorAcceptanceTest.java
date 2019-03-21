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
package tech.pegasys.pantheon.tests.acceptance.permissioning;

import static tech.pegasys.pantheon.ethereum.permissioning.WhitelistPersistor.WHITELIST_TYPE;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public class WhitelistPersistorAcceptanceTest extends AcceptanceTestBase {

  private Node node;
  private Account senderA;
  private Account senderB;
  private Path tempFile;

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode2 =
      "enode://5f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode3 =
      "enode://4f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";

  @Before
  public void setUp() throws Exception {
    senderA = accounts.getPrimaryBenefactor();
    senderB = accounts.getSecondaryBenefactor();
    tempFile = Files.createTempFile("test", "test");

    this.node =
        permissionedNodeBuilder
            .name("node")
            .nodesConfigFile(tempFile)
            .nodesPermittedInConfig(new ArrayList<>())
            .accountsConfigFile(tempFile)
            .accountsPermittedInConfig(Collections.singletonList(senderA.getAddress()))
            .build();

    cluster.start(this.node);
  }

  @Test
  public void manipulatedAccountsWhitelistIsPersisted() {
    node.verify(
        perm.expectPermissioningWhitelistFileKeyValue(
            WHITELIST_TYPE.ACCOUNTS, tempFile, senderA.getAddress()));

    node.execute(transactions.addAccountsToWhitelist(senderB.getAddress()));
    node.verify(perm.expectAccountsWhitelist(senderA.getAddress(), senderB.getAddress()));
    node.verify(
        perm.expectPermissioningWhitelistFileKeyValue(
            WHITELIST_TYPE.ACCOUNTS, tempFile, senderA.getAddress(), senderB.getAddress()));

    node.execute(transactions.removeAccountsFromWhitelist(senderB.getAddress()));
    node.verify(perm.expectAccountsWhitelist(senderA.getAddress()));
    node.verify(
        perm.expectPermissioningWhitelistFileKeyValue(
            WHITELIST_TYPE.ACCOUNTS, tempFile, senderA.getAddress()));

    node.execute(transactions.removeAccountsFromWhitelist(senderA.getAddress()));
    node.verify(perm.expectAccountsWhitelist());
    node.verify(perm.expectPermissioningWhitelistFileKeyValue(WHITELIST_TYPE.ACCOUNTS, tempFile));
  }

  @Test
  public void manipulatedNodesWhitelistIsPersisted() {
    node.verify(perm.addNodesToWhitelist(enode1, enode2));
    node.verify(
        perm.expectPermissioningWhitelistFileKeyValue(
            WHITELIST_TYPE.NODES, tempFile, enode1, enode2));

    node.verify(perm.removeNodesFromWhitelist(enode1));
    node.verify(
        perm.expectPermissioningWhitelistFileKeyValue(WHITELIST_TYPE.NODES, tempFile, enode2));

    node.verify(perm.addNodesToWhitelist(enode1, enode3));
    node.verify(
        perm.expectPermissioningWhitelistFileKeyValue(
            WHITELIST_TYPE.NODES, tempFile, enode2, enode1, enode3));
  }
}
