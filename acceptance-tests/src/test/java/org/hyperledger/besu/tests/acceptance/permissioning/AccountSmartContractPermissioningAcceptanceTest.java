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
package org.hyperledger.besu.tests.acceptance.permissioning;

import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

public class AccountSmartContractPermissioningAcceptanceTest
    extends AccountSmartContractPermissioningAcceptanceTestBase {

  private Node node;
  private Account allowedSender;
  private Account otherAccount;

  @Before
  public void setUp() {
    node = permissionedNode("node1", Collections.emptyList());
    permissionedCluster.start(node);

    allowedSender = accounts.createAccount("authorized-account");
    otherAccount = accounts.createAccount("other-account");

    // ensure primary benefactor is permitted (account used to permit/forbid other accounts)
    node.execute(allowAccount(accounts.getPrimaryBenefactor()));
    node.verify(accountIsAllowed(accounts.getPrimaryBenefactor()));

    // ensure allowedSender has got some ether available
    node.execute(accountTransactions.createTransfer(allowedSender, 10));
    node.verify(allowedSender.balanceEquals(10));
  }

  @Test
  public void allowedAccountCanTransferValue() {
    node.execute(allowAccount(allowedSender));
    node.verify(accountIsAllowed(allowedSender));

    node.execute(accountTransactions.createTransfer(allowedSender, otherAccount, 5));
    node.verify(otherAccount.balanceEquals(5));
  }

  @Test
  public void forbiddenAccountCannotTransferValue() {
    node.execute(forbidAccount(allowedSender));
    node.verify(accountIsForbidden(allowedSender));

    node.execute(accountTransactions.createTransfer(allowedSender, otherAccount, 5));
    node.verify(otherAccount.balanceDoesNotChange(0));
  }
}
