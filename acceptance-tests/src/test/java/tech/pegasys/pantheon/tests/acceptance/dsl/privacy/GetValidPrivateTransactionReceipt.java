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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eea;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ResponseTypes;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transactions;

public abstract class GetValidPrivateTransactionReceipt {

  private Eea eea;
  private Transactions transactions;

  GetValidPrivateTransactionReceipt(final Eea eea, final Transactions transactions) {
    this.eea = eea;
    this.transactions = transactions;
  }

  ResponseTypes.PrivateTransactionReceipt getPrivateTransactionReceipt(
      final PantheonNode node, final String transactionHash) {

    waitFor(() -> node.verify(eea.expectSuccessfulTransactionReceipt(transactionHash)));
    ResponseTypes.PrivateTransactionReceipt privateTxReceipt =
        node.execute(transactions.getPrivateTransactionReceipt(transactionHash));
    return privateTxReceipt;
  }
}
