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

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;

import java.math.BigInteger;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AccountLocalConfigPermissioningAcceptanceTest extends AcceptanceTestBase {

  private Node node;
  private Account senderA;
  private Account senderB;

  @BeforeEach
  public void setUp() throws Exception {
    senderA = accounts.getPrimaryBenefactor();
    senderB = accounts.getSecondaryBenefactor();

    node =
        permissionedNodeBuilder
            .name("node")
            .accountsPermittedInConfig(Collections.singletonList(senderA.getAddress()))
            .build();

    cluster.start(node);
  }

  @Test
  public void onlyAllowedAccountCanSubmitTransactions() {
    Account beneficiary = accounts.createAccount("beneficiary");

    node.execute(accountTransactions.createTransfer(senderA, beneficiary, 1));
    node.verify(beneficiary.balanceEquals(1));

    verifyTransferForbidden(senderB, beneficiary);
  }

  @Test
  public void manipulatingAccountsAllowlistViaJsonRpc() {
    Account beneficiary = accounts.createAccount("beneficiary");
    node.verify(beneficiary.balanceEquals(0));

    verifyTransferForbidden(senderB, beneficiary);

    node.execute(permissioningTransactions.addAccountsToAllowlist(senderB.getAddress()));
    node.verify(perm.expectAccountsAllowlist(senderA.getAddress(), senderB.getAddress()));

    node.execute(accountTransactions.createTransfer(senderB, beneficiary, 1));
    node.verify(beneficiary.balanceEquals(1));

    node.execute(permissioningTransactions.removeAccountsFromAllowlist(senderB.getAddress()));
    node.verify(perm.expectAccountsAllowlist(senderA.getAddress()));
    verifyTransferForbidden(senderB, beneficiary);
  }

  private void verifyTransferForbidden(final Account sender, final Account beneficiary) {
    BigInteger nonce = node.execute(ethTransactions.getTransactionCount(sender.getAddress()));
    TransferTransaction transfer =
        accountTransactions.createTransfer(sender, beneficiary, 1, nonce);
    node.verify(
        eth.expectEthSendRawTransactionException(
            transfer.signedTransactionData(),
            "Sender account not authorized to send transactions"));
  }
}
