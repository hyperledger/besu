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

import static java.util.Arrays.asList;

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class TransactionPoolReplacementHandler implements TransactionPoolReplacementRule {
  private final List<TransactionPoolReplacementRule> rules;

  public TransactionPoolReplacementHandler() {
    this(asList(new TransactionReplacementByPriceRule()));
  }

  @VisibleForTesting
  TransactionPoolReplacementHandler(final List<TransactionPoolReplacementRule> rules) {
    this.rules = rules;
  }

  @Override
  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final Optional<Long> baseFee) {
    assert existingTransactionInfo != null;
    if (newTransactionInfo == null) {
      return false;
    }
    return rules.stream()
        .anyMatch(rule -> rule.shouldReplace(existingTransactionInfo, newTransactionInfo, baseFee));
  }
}
