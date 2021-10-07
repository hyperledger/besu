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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.ethereum.MainnetBlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor.Result;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MergeBlockValidator extends MainnetBlockValidator {

  private final MergeBlockProcessor mergeBlockProcessor;
  private final MergeContext mergeContext = PostMergeContext.get();
  /**
   * BlockValidator wrapping MainnetBlockValidator for use with merge.
   *
   * @param blockHeaderValidator configured BlockHeaderValidator
   * @param blockBodyValidator configured BlockBodyValidator
   * @param mergeBlockProcessor configured BlockProcessor, should be instance of MergeBlockProcessor
   * @param badBlockManager configured BadBlockManager
   * @param __ unused GoQuorumPrivacyParameters, here for functional constructor convenience
   */
  public MergeBlockValidator(
      final BlockHeaderValidator blockHeaderValidator,
      final BlockBodyValidator blockBodyValidator,
      final BlockProcessor mergeBlockProcessor,
      final BadBlockManager badBlockManager,
      final Optional<GoQuorumPrivacyParameters> __) {
    super(blockHeaderValidator, blockBodyValidator, mergeBlockProcessor, badBlockManager);
    this.mergeBlockProcessor = (MergeBlockProcessor) mergeBlockProcessor;
  }

  @Override
  protected Result processBlock(
      final ProtocolContext context, final MutableWorldState worldState, final Block block) {
    if (!mergeContext.isPostMerge() || mergeContext.isSyncing()) {
      return super.processBlock(context, worldState, block);
    }

    if (mergeContext.validateCandidateHead(block.getHeader())) {
      MergeBlockProcessor.CandidateBlock newCandidate =
          mergeBlockProcessor.executeBlock(
              context.getBlockchain(),
              worldState,
              block.getHeader(),
              block.getBody().getTransactions());

      mergeContext.setCandidateBlock(newCandidate);

      return newCandidate.getBlockProcessorResult().get();
    }
    return failedResult;
  }

  private static final Result failedResult =
      new Result() {
        @Override
        public List<TransactionReceipt> getReceipts() {
          return Collections.emptyList();
        }

        @Override
        public List<TransactionReceipt> getPrivateReceipts() {
          return Collections.emptyList();
        }

        @Override
        public boolean isSuccessful() {
          return false;
        }
      };
}
