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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

public class TransactionReplacementByPriceRule implements TransactionPoolReplacementRule {
  @Override
  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo, final TransactionInfo newTransactionInfo) {
    assert existingTransactionInfo.getTransaction() != null
        && newTransactionInfo.getTransaction() != null;
    return newTransactionInfo
            .getTransaction()
            .getGasPrice()
            .compareTo(existingTransactionInfo.getTransaction().getGasPrice())
        > 0;
  }
}
