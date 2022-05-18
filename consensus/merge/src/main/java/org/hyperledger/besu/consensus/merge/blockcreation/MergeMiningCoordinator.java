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
package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes32;

public interface MergeMiningCoordinator extends MiningCoordinator {
  PayloadIdentifier preparePayload(
      final BlockHeader parentHeader,
      final Long timestamp,
      final Bytes32 random,
      final Address feeRecipient);

  Result executeBlock(final Block block);

  ForkchoiceResult updateForkChoice(
      final BlockHeader newHead, final Hash finalizedBlockHash, final Hash safeBlockHash);

  Optional<Hash> getLatestValidAncestor(Hash blockHash);

  Optional<Hash> getLatestValidAncestor(BlockHeader blockheader);

  boolean latestValidAncestorDescendsFromTerminal(final BlockHeader blockHeader);

  boolean isDescendantOf(final BlockHeader ancestorBlock, final BlockHeader newBlock);

  boolean isBackwardSyncing();

  CompletableFuture<Void> appendNewPayloadToSync(Block newPayload);

  Optional<BlockHeader> getOrSyncHeaderByHash(Hash blockhash);

  boolean isMiningBeforeMerge();

  class ForkchoiceResult {
    private final Optional<String> errorMessage;
    private final Optional<BlockHeader> newFinalized;
    private final Optional<BlockHeader> newHead;
    private final Optional<Hash> latestValid;

    private ForkchoiceResult(
        final Optional<String> errorMessage,
        final Optional<BlockHeader> newFinalized,
        final Optional<BlockHeader> newHead,
        final Optional<Hash> latestValid) {
      this.errorMessage = errorMessage;
      this.newFinalized = newFinalized;
      this.newHead = newHead;
      this.latestValid = latestValid;
    }

    public static ForkchoiceResult withFailure(
        final String errorMessage, final Optional<Hash> latestValid) {
      return new ForkchoiceResult(
          Optional.of(errorMessage), Optional.empty(), Optional.empty(), latestValid);
    }

    public static ForkchoiceResult withResult(
        final Optional<BlockHeader> newFinalized, final Optional<BlockHeader> newHead) {
      return new ForkchoiceResult(Optional.empty(), newFinalized, newHead, Optional.empty());
    }

    public Optional<String> getErrorMessage() {
      return errorMessage;
    }

    public Optional<BlockHeader> getNewFinalized() {
      return newFinalized;
    }

    public Optional<BlockHeader> getNewHead() {
      return newHead;
    }

    public Optional<Hash> getLatestValid() {
      return latestValid;
    }

    public boolean isFailed() {
      return errorMessage.isPresent();
    }

    public boolean isSuccessful() {
      return newHead.isPresent() || newFinalized.isPresent();
    }

    public boolean isUnknown() {
      return errorMessage.isEmpty() && newFinalized.isEmpty();
    }
  }
}
