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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionInfo;
import org.hyperledger.besu.util.number.Percentage;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;

public class TransactionPoolReplacementHandler {
  private final List<TransactionPoolReplacementRule> rules;

  public TransactionPoolReplacementHandler(final Percentage priceBump) {
    this(
        asList(
            new TransactionReplacementByGasPriceRule(priceBump),
            new TransactionReplacementByFeeMarketRule(priceBump)));
  }

  @VisibleForTesting
  TransactionPoolReplacementHandler(final List<TransactionPoolReplacementRule> rules) {
    this.rules = rules;
  }

  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final BlockHeader chainHeadHeader) {
    assert existingTransactionInfo != null;
    return newTransactionInfo != null
        && rules.stream()
            .anyMatch(
                rule ->
                    rule.shouldReplace(
                        existingTransactionInfo, newTransactionInfo, chainHeadHeader.getBaseFee()));
  }
}
