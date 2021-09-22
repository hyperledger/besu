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

import org.hyperledger.besu.consensus.merge.MergeBlockProcessor.CandidateBlock;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class MergeContext {

  // TODO: configure static terminal total difficulty,
  //      a value of 0 means we will be unable to sync PoW blocks
  public static final Difficulty STATIC_TERMINAL_TOTAL_DIFFICULTY = Difficulty.ZERO;

  private final AtomicReference<Difficulty> terminalTotalDifficulty =
      new AtomicReference<>(STATIC_TERMINAL_TOTAL_DIFFICULTY);

  public enum ExecutionStatus {
    VALID,
    INVALID,
    KNOWN;
  }

  public enum ConsensusStatus {
    VALID,
    INVALID;

    public boolean equalsIgnoreCase(final String status) {
      return name().equalsIgnoreCase(status);
    }
  }

  // current candidate block from consensus engine
  AtomicReference<CandidateBlock> candidateBlock = new AtomicReference<>();

  // latest finalized block
  // TODO: persist this to storage https://github.com/ConsenSys/protocol-misc/issues/478
  AtomicReference<BlockHeader> lastFinalized = new AtomicReference<>();

  public void setTerminalTotalDifficulty(final Difficulty newTerminalTotalDifficulty) {
    this.terminalTotalDifficulty.set(newTerminalTotalDifficulty);
  }

  public boolean isPostMerge(final BlockHeader blockheader) {
    // TODO: this will probably erroneously return true for the transition block. fix
    return terminalTotalDifficulty.get().lessOrEqualThan(blockheader.getDifficulty());
  }

  public void updateForkChoice(final Hash headBlockHash, final Hash finalizedBlockHash) {
    candidateBlock.get().updateForkChoice(headBlockHash, finalizedBlockHash);
  }

  public Optional<BlockHeader> getFinalized() {
    return Optional.ofNullable(lastFinalized.get());
  }

  public boolean validateCandidateHead(final BlockHeader candidateHeader) {
    return Optional.ofNullable(lastFinalized.get())
        .map(finalized -> candidateHeader.getNumber() <= finalized.getNumber())
        .orElse(Boolean.TRUE);
  }

  void setCandidateBlock(final CandidateBlock candidate) {
    this.candidateBlock.set(candidate);
  }

  public boolean setConsensusValidated(final Hash candidateHash) {
    CandidateBlock candidate = candidateBlock.get();

    if (candidate != null && candidate.getBlockhash().equals(candidateHash)) {
      candidate.setConsensusValidated();
      return true;
    }
    return false;
  }
}
