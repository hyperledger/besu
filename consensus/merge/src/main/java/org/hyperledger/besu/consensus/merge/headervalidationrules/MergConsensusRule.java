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
package org.hyperledger.besu.consensus.merge.headervalidationrules;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class MergConsensusRule implements AttachedBlockHeaderValidationRule {
  private static final Logger LOG = LogManager.getLogger(MergConsensusRule.class);

  protected boolean isProofOfStakeBlock(final BlockHeader header, final ProtocolContext context) {

    Optional<Difficulty> currentChainTotalDifficulty =
        context.getBlockchain().getTotalDifficultyByHash(header.getParentHash());
    Difficulty configuredTotalTerminalDifficulty =
        context.getConsensusContext(MergeContext.class).getTerminalTotalDifficulty();

    if (currentChainTotalDifficulty.isEmpty()) {
      LOG.warn("unable to get total difficulty, parent {} not found", header.getParentHash());
      return false;
    }
    if (isTerminalProofOfWorkBlock(header, context)) {
      // ttd block looks like proof of stake, but is the last proof of work
      return false;
    }
    if (currentChainTotalDifficulty
        .get()
        .add(header.getDifficulty() == null ? Difficulty.ZERO : header.getDifficulty())
        .greaterThan(configuredTotalTerminalDifficulty)) {
      return true;
    } else { // still PoWing
      return false;
    }
  }

  protected boolean isTerminalProofOfWorkBlock(
      final BlockHeader header, final ProtocolContext context) {
    Optional<Difficulty> currentChainTotalDifficulty =
        context.getBlockchain().getTotalDifficultyByHash(header.getParentHash());
    Difficulty configuredTotalTerminalDifficulty =
        context.getConsensusContext(MergeContext.class).getTerminalTotalDifficulty();

    if (currentChainTotalDifficulty.isEmpty()) {
      LOG.warn("unable to get total difficulty, parent {} not found", header.getParentHash());
      return false;
    }
    if (currentChainTotalDifficulty
            .get()
            .add(header.getDifficulty() == null ? Difficulty.ZERO : header.getDifficulty())
            .greaterThan(configuredTotalTerminalDifficulty) // adding would equal or go over limit
        && currentChainTotalDifficulty
            .get()
            .lessThan(configuredTotalTerminalDifficulty) // parent was under
    ) {
      return true;
    } else {
      return false;
    }
  }
}
