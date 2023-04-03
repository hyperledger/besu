/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.Optional;

/** The Merge block processor. */
public class MergeBlockProcessor extends MainnetBlockProcessor {
  private final MergeContext mergeContext;

  /**
   * Instantiates a new Merge block processor.
   *
   * @param transactionProcessor the transaction processor
   * @param transactionReceiptFactory the transaction receipt factory
   * @param blockReward the block reward
   * @param miningBeneficiaryCalculator the mining beneficiary calculator
   * @param skipZeroBlockRewards the skip zero block rewards
   * @param goQuorumPrivacyParameters the go quorum privacy parameters
   * @param protocolSchedule the header based protocol scheduler
   */
  public MergeBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters,
      final ProtocolSchedule protocolSchedule) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        goQuorumPrivacyParameters,
        protocolSchedule);
    this.mergeContext = PostMergeContext.get();
  }

  @Override
  protected boolean rewardCoinbase(
      final MutableWorldState worldState,
      final BlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {

    if (!mergeContext.isPostMerge()) {
      return super.rewardCoinbase(worldState, header, ommers, skipZeroBlockRewards);
    }
    // do not issue block rewards post-merge
    return true;
  }
}
