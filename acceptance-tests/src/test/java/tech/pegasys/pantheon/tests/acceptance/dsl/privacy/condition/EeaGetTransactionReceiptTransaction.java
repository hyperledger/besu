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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy.condition;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;

import org.web3j.protocol.eea.response.PrivateTransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.pantheon.Pantheon;
import org.web3j.tx.response.PollingPrivateTransactionReceiptProcessor;

public class EeaGetTransactionReceiptTransaction implements Transaction<PrivateTransactionReceipt> {

  private final String transactionHash;

  public EeaGetTransactionReceiptTransaction(final String transactionHash) {
    this.transactionHash = transactionHash;
  }

  @Override
  public PrivateTransactionReceipt execute(final NodeRequests node) {
    final Pantheon pantheon = node.privacy().getPantheonClient();
    final PollingPrivateTransactionReceiptProcessor receiptProcessor =
        new PollingPrivateTransactionReceiptProcessor(pantheon, 3000, 3);
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
