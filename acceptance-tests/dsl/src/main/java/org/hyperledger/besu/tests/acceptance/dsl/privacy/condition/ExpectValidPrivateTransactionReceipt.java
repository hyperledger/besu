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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.condition;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.PrivacyTransactions;

import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;

public class ExpectValidPrivateTransactionReceipt implements PrivateCondition {
  private final PrivacyTransactions transactions;
  private final String transactionHash;
  private final PrivateTransactionReceipt expectedReceipt;
  private final boolean ignoreOutput;

  public ExpectValidPrivateTransactionReceipt(
      final PrivacyTransactions transactions,
      final String transactionHash,
      final PrivateTransactionReceipt expectedReceipt) {
    this(transactions, transactionHash, expectedReceipt, false);
  }

  public ExpectValidPrivateTransactionReceipt(
      final PrivacyTransactions transactions,
      final String transactionHash,
      final PrivateTransactionReceipt expectedReceipt,
      final boolean ignoreOutput) {

    this.transactions = transactions;
    this.transactionHash = transactionHash;
    this.expectedReceipt = expectedReceipt;
    this.ignoreOutput = ignoreOutput;
  }

  @Override
  public void verify(final PrivacyNode node) {
    final PrivateTransactionReceipt actualReceipt =
        node.execute(transactions.getPrivateTransactionReceipt(transactionHash));
    if (ignoreOutput) {
      // output can be ignored if it is checked separately
      assertThat(actualReceipt)
          .usingRecursiveComparison()
          .ignoringFields(
              "commitmentHash",
              "logs",
              "blockHash",
              "blockNumber",
              "logsBloom",
              "transactionIndex",
              "output")
          // TODO: The fields blockHash, blockNumber, logsBloom, transactionIndex and output have to
          // be ignored as
          // the class org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt does not
          // contain
          // these fields. Once web3j has been updated these ignores can be removed.
          .isEqualTo(expectedReceipt);
    } else {
      assertThat(actualReceipt)
          .usingRecursiveComparison()
          .ignoringFields(
              "commitmentHash", "logs", "blockHash", "blockNumber", "logsBloom", "transactionIndex")
          // TODO: The fields blockHash, blockNumber, logsBloom and transactionIndex have to be
          // ignored as the class
          // org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt does not contain
          // these fields. Once web3j has been updated these ignores can be removed.
          .isEqualTo(expectedReceipt);
    }
    assertThat(actualReceipt.getLogs().size()).isEqualTo(expectedReceipt.getLogs().size());

    for (int i = 0; i < expectedReceipt.getLogs().size(); i++) {
      assertThat(actualReceipt.getLogs().get(i))
          .usingRecursiveComparison()
          .ignoringFields("blockHash", "blockNumber")
          .isEqualTo(expectedReceipt.getLogs().get(i));
    }
  }
}
