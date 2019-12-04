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

import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.response.PollingPrivateTransactionReceiptProcessor;

public class PrivGetTransactionReceiptTransaction
    implements Transaction<PrivateTransactionReceipt> {

  private final String transactionHash;

  public PrivGetTransactionReceiptTransaction(final String transactionHash) {
    this.transactionHash = transactionHash;
  }

  @Override
  public PrivateTransactionReceipt execute(final NodeRequests node) {
    final Besu besu = node.privacy().getBesuClient();
    final PollingPrivateTransactionReceiptProcessor receiptProcessor =
        new PollingPrivateTransactionReceiptProcessor(besu, 1000, 15);
    try {
      final PrivateTransactionReceipt result =
          receiptProcessor.waitForTransactionReceipt(transactionHash);
      assertThat(result).isNotNull();
      return result;
    } catch (final IOException | TransactionException e) {
      throw new RuntimeException(e);
    }
  }
}
