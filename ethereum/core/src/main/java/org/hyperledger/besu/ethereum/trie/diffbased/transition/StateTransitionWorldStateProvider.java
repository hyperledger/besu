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
package org.hyperledger.besu.ethereum.trie.diffbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class StateTransitionWorldStateProvider implements WorldStateArchive {

  private final BonsaiWorldStateProvider bonsaiWorldStateProvider;
  private final VerkleWorldStateProvider verkleWorldStateProvider;

  private final long verkleMilestone;
  private final Blockchain blockchain;

  public StateTransitionWorldStateProvider(
      final BonsaiWorldStateProvider bonsaiWorldStateProvider,
      final VerkleWorldStateProvider verkleWorldStateProvider,
      final long verkleMilestone,
      final Blockchain blockchain) {
    this.bonsaiWorldStateProvider = bonsaiWorldStateProvider;
    this.verkleWorldStateProvider = verkleWorldStateProvider;
    this.verkleMilestone = verkleMilestone;
    this.blockchain = blockchain;
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    return verkleWorldStateProvider
        .get(rootHash, blockHash)
        .or(() -> bonsaiWorldStateProvider.get(rootHash, blockHash));
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return verkleWorldStateProvider.isWorldStateAvailable(rootHash, blockHash)
        || bonsaiWorldStateProvider.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getWorldState(
      final WorldStateQueryParams worldStateQueryParams) {

    // Retrieve Bonsai state and reorg if needed
    final Optional<BonsaiWorldState> bonsaiWorldState =
        getBonsaiTransitionWorldState(worldStateQueryParams);
    // Retrieve verkle state and reorg if needed
    final Optional<VerkleWorldState> verkleWorldState =
        getVerkleTransitionWorldState(worldStateQueryParams);

    if (bonsaiWorldState.isEmpty() && verkleWorldState.isEmpty()) {
      return Optional.empty();
    }

    final boolean isBonsaiWorldState =
        bonsaiWorldState
            .get()
            .getWorldStateBlockHash()
            .equals(worldStateQueryParams.getBlockHash());
    final boolean isVerkleWorldState =
        verkleWorldState
            .get()
            .getWorldStateBlockHash()
            .equals(worldStateQueryParams.getBlockHash());
    if (isBonsaiWorldState || isVerkleWorldState) {
      // Combine both states if they are present.
      return Optional.of(
          new StateTransitionWorldState(
              bonsaiWorldState.get(), verkleWorldState.get(), isVerkleWorldState, verkleMilestone));
    }
    return Optional.empty();
  }

  @Override
  public MutableWorldState getWorldState() {
    final BonsaiWorldState bonsaiState =
        (BonsaiWorldState) bonsaiWorldStateProvider.getWorldState();
    final VerkleWorldState verkleState =
        (VerkleWorldState) verkleWorldStateProvider.getWorldState();
    final boolean isVerkleActive =
        blockchain.getChainHeadHeader().getTimestamp() >= verkleMilestone;
    return new StateTransitionWorldState(bonsaiState, verkleState, isVerkleActive, verkleMilestone);
  }

  private Optional<BonsaiWorldState> getBonsaiTransitionWorldState(
      final WorldStateQueryParams worldStateQueryParams) {
    return bonsaiWorldStateProvider
        .getWorldState(worldStateQueryParams)
        .map(BonsaiWorldState.class::cast);
  }

  private Optional<VerkleWorldState> getVerkleTransitionWorldState(
      final WorldStateQueryParams worldStateQueryParams) {
    final VerkleWorldState initialVerkleState =
        (VerkleWorldState) verkleWorldStateProvider.getWorldState();
    // If the initial state's block hash is zero, it indicates the chain's head
    // has not yet passed the Verkle fork (zero block hash does not exist in a valid chain).
    if (initialVerkleState.getWorldStateBlockHash().isZero()) {
      return worldStateQueryParams.shouldWorldStateUpdateHead()
          ? Optional.of(initialVerkleState)
          : Optional.empty();
    }
    // Otherwise, retrieve the valid one from verkle provider
    return verkleWorldStateProvider
        .getWorldState(worldStateQueryParams)
        .map(VerkleWorldState.class::cast);
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    final boolean isVerkleActive = blockHeader.getTimestamp() >= verkleMilestone;
    if (isVerkleActive) {
      verkleWorldStateProvider.resetArchiveStateTo(blockHeader);
    } else {
      bonsaiWorldStateProvider.resetArchiveStateTo(blockHeader);
    }
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    return Optional.empty();
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void heal(final Optional<Address> maybeAccountToRepair, final Bytes location) {
    // can only work for bonsai
    bonsaiWorldStateProvider.heal(maybeAccountToRepair, location);
  }

  @Override
  public void close() throws IOException {
    bonsaiWorldStateProvider.close();
    verkleWorldStateProvider.close();
  }
}
