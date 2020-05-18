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
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionInfo;
import org.hyperledger.besu.util.number.Percentage;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

public class TransactionPoolReplacementHandler {
  private final List<TransactionPoolReplacementRule> rules;
  private final Optional<EIP1559> eip1559;

  public TransactionPoolReplacementHandler(
      final Optional<EIP1559> eip1559, final Percentage priceBump) {
    this(asList(new TransactionReplacementByPriceRule(priceBump)), eip1559);
  }

  @VisibleForTesting
  TransactionPoolReplacementHandler(
      final List<TransactionPoolReplacementRule> rules, final Optional<EIP1559> eip1559) {
    this.rules = rules;
    this.eip1559 = eip1559;
  }

  public boolean shouldReplace(
      final TransactionInfo existingTransactionInfo,
      final TransactionInfo newTransactionInfo,
      final BlockHeader chainHeadHeader) {
    assert existingTransactionInfo != null;
    if (newTransactionInfo == null) {
      return false;
    }
    return eip1559
            .map(
                eip ->
                    eip.isValidTransaction(
                        chainHeadHeader.getNumber(), newTransactionInfo.getTransaction()))
            .orElse(newTransactionInfo.getTransaction().isFrontierTransaction())
        && rules.stream()
            .anyMatch(
                rule ->
                    rule.shouldReplace(
                        existingTransactionInfo, newTransactionInfo, chainHeadHeader.getBaseFee()));
  }
}
