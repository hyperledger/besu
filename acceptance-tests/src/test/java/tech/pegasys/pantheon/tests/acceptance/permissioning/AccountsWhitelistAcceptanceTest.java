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
package tech.pegasys.pantheon.tests.acceptance.permissioning;

import static org.web3j.utils.Convert.Unit.ETHER;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account.TransferTransaction;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class AccountsWhitelistAcceptanceTest extends AcceptanceTestBase {

  private Node node;
  private Account senderA;
  private Account senderB;

  @Before
  public void setUp() throws Exception {
    senderA = accounts.getPrimaryBenefactor();
    senderB = accounts.getSecondaryBenefactor();

    node =
        permissionedNodeBuilder
            .name("node")
            .accountsPermittedInConfig(Arrays.asList(senderA.getAddress()))
            .build();

    cluster.start(node);
  }

  @Test
  public void onlyAllowedAccountCanSubmitTransactions() {
    Account beneficiary = accounts.createAccount("beneficiary");

    node.execute(transactions.createTransfer(senderA, beneficiary, 1));
    node.verify(beneficiary.balanceEquals("1", ETHER));

    verifyTransferForbidden(senderB, beneficiary);
  }

  @Test
  public void manipulatingAccountsWhitelistViaJsonRpc() {
    Account beneficiary = accounts.createAccount("beneficiary");
    node.verify(beneficiary.balanceEquals("0", ETHER));

    verifyTransferForbidden(senderB, beneficiary);

    node.execute(transactions.addAccountsToWhitelist(senderB.getAddress()));
    node.verify(perm.expectAccountsWhitelist(senderA.getAddress(), senderB.getAddress()));

    node.execute(transactions.createTransfer(senderB, beneficiary, 1));
    node.verify(beneficiary.balanceEquals("1", ETHER));

    node.execute(transactions.removeAccountsFromWhitelist(senderB.getAddress()));
    node.verify(perm.expectAccountsWhitelist(senderA.getAddress()));
    verifyTransferForbidden(senderB, beneficiary);
  }

  private void verifyTransferForbidden(final Account sender, final Account beneficiary) {
    BigInteger nonce = node.execute(transactions.getTransactionCount(sender.getAddress()));
    TransferTransaction transfer = transactions.createTransfer(sender, beneficiary, 1, nonce);
    node.verify(
        eth.sendRawTransactionExceptional(
            transfer.signedTransactionData(),
            "Sender account not authorized to send transactions"));
  }
}
