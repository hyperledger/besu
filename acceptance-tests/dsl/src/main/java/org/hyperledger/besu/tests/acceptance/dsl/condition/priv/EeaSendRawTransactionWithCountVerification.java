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
package org.hyperledger.besu.tests.acceptance.dsl.condition.priv;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.EeaSendRawTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetTransactionCountTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivGetTransactionReceiptTransaction;

import org.assertj.core.api.Assertions;

public class EeaSendRawTransactionWithCountVerification implements Condition {

  private final EeaSendRawTransaction sendRawTransactionTransaction;
  private final Object[] transactionCountParams;

  public EeaSendRawTransactionWithCountVerification(
      final EeaSendRawTransaction sendRawTransactionTransaction,
      final String transactionCountSender,
      final String transactionCountPrivacyGroupId) {
    this.sendRawTransactionTransaction = sendRawTransactionTransaction;
    this.transactionCountParams =
        new String[] {transactionCountSender, transactionCountPrivacyGroupId};
  }

  @Override
  public void verify(final Node node) {
    final PrivGetTransactionCountTransaction getTransactionCountTransaction =
        new PrivGetTransactionCountTransaction(transactionCountParams);
    WaitUtils.waitFor(
        () -> Assertions.assertThat(node.execute(getTransactionCountTransaction)).isEqualTo(0));

    final Hash transactionHash = node.execute(sendRawTransactionTransaction);
    assertThat(transactionHash).isInstanceOf(Hash.class);

    WaitUtils.waitFor(
        () ->
            Assertions.assertThat(
                    node.execute(new PrivGetTransactionReceiptTransaction(transactionHash)))
                .isNotNull());

    WaitUtils.waitFor(
        () -> Assertions.assertThat(node.execute(getTransactionCountTransaction)).isEqualTo(1));
  }
}
