/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;

public record ParallelizedTransactionContext(
    DiffBasedWorldStateUpdateAccumulator<?> transactionAccumulator,
    TransactionProcessingResult transactionProcessingResult,
    boolean isMiningBeneficiaryTouchedPreRewardByTransaction,
    Wei miningBeneficiaryReward) {

  public static class Builder {
    private DiffBasedWorldStateUpdateAccumulator<?> transactionAccumulator;
    private TransactionProcessingResult transactionProcessingResult;
    private boolean isMiningBeneficiaryTouchedPreRewardByTransaction;
    private Wei miningBeneficiaryReward;

    public Builder transactionAccumulator(
        final DiffBasedWorldStateUpdateAccumulator<?> transactionAccumulator) {
      this.transactionAccumulator = transactionAccumulator;
      return this;
    }

    public Builder transactionProcessingResult(
        final TransactionProcessingResult transactionProcessingResult) {
      this.transactionProcessingResult = transactionProcessingResult;
      return this;
    }

    public Builder isMiningBeneficiaryTouchedPreRewardByTransaction(
        final boolean isMiningBeneficiaryTouchedPreRewardByTransaction) {
      this.isMiningBeneficiaryTouchedPreRewardByTransaction =
          isMiningBeneficiaryTouchedPreRewardByTransaction;
      return this;
    }

    public Builder miningBeneficiaryReward(final Wei miningBeneficiaryReward) {
      this.miningBeneficiaryReward = miningBeneficiaryReward;
      return this;
    }

    public ParallelizedTransactionContext build() {
      return new ParallelizedTransactionContext(
          transactionAccumulator,
          transactionProcessingResult,
          isMiningBeneficiaryTouchedPreRewardByTransaction,
          miningBeneficiaryReward);
    }
  }
}
