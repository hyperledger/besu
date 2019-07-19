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

import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account.TransferTransaction;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class AccountLocalAndOnChainPermissioningAcceptanceTest
    extends AccountSmartContractPermissioningAcceptanceTestBase {

  private Account senderC;

  @Before
  public void setUp() {
    senderC = accounts.createAccount("Account-C");
  }

  @Test
  public void testAccountCannotSendTxWhenNotOnLocalWhiteList() {
    // OnChain whitelist: Primary, Secondary, C
    // Local whitelist: Primary, Secondary

    final Node node =
        permissionedNode(
            "node1",
            Arrays.asList(
                accounts.getPrimaryBenefactor().getAddress(),
                accounts.getSecondaryBenefactor().getAddress()));
    permissionedCluster.start(node);

    // ensure SenderC has got some ether available
    node.execute(accountTransactions.createTransfer(senderC, 10));
    node.verify(senderC.balanceEquals(10));

    // add accounts to onChain Whitelist
    node.execute(allowAccount(accounts.getPrimaryBenefactor()));
    node.verify(accountIsAllowed(accounts.getPrimaryBenefactor()));

    node.execute(allowAccount(accounts.getSecondaryBenefactor()));
    node.verify(accountIsAllowed(accounts.getSecondaryBenefactor()));

    node.execute(allowAccount(senderC));
    node.verify(accountIsAllowed(senderC));

    // sender C should not be able to send Tx
    verifyTransferForbidden(node, senderC, accounts.getSecondaryBenefactor());
  }

  @Test
  public void testAccountCannotSendTxWhenNotOnOnChainWhiteList() {
    // OnChain Whitelist: Primary, Secondary, Receiver
    // Local Whitelist: Primary, Secondary, C, Receiver

    final Account receiverAccount = accounts.createAccount("Rec-A");

    final Node node =
        permissionedNode(
            "node1",
            Arrays.asList(
                accounts.getPrimaryBenefactor().getAddress(),
                accounts.getSecondaryBenefactor().getAddress(),
                senderC.getAddress(),
                receiverAccount.getAddress()));
    permissionedCluster.start(node);

    // ensure SenderC has got some ether available
    node.execute(accountTransactions.createTransfer(senderC, 10));
    node.verify(senderC.balanceEquals(10));

    // add accounts to onChain Whitelist
    node.execute(allowAccount(accounts.getPrimaryBenefactor()));
    node.verify(accountIsAllowed(accounts.getPrimaryBenefactor()));

    node.execute(allowAccount(accounts.getSecondaryBenefactor()));
    node.verify(accountIsAllowed(accounts.getSecondaryBenefactor()));

    node.execute(allowAccount(receiverAccount));
    node.verify(accountIsAllowed(receiverAccount));

    // verify senderC is forbidden because it is not on OnChain whitelist
    node.verify(accountIsForbidden(senderC));

    // sender C should not be able to send Tx as well
    node.execute(accountTransactions.createTransfer(senderC, receiverAccount, 1));
    node.verify(receiverAccount.balanceDoesNotChange(0));

    // final check, other account should be able to send tx
    node.execute(
        accountTransactions.createTransfer(accounts.getPrimaryBenefactor(), receiverAccount, 5));
    node.verify(receiverAccount.balanceEquals(5));
  }

  private void verifyTransferForbidden(
      final Node node, final Account sender, final Account beneficiary) {
    BigInteger nonce = node.execute(ethTransactions.getTransactionCount(sender.getAddress()));
    TransferTransaction transfer =
        accountTransactions.createTransfer(sender, beneficiary, 1, nonce);
    node.verify(
        eth.sendRawTransactionExceptional(
            transfer.signedTransactionData(),
            "Sender account not authorized to send transactions"));
  }
}
