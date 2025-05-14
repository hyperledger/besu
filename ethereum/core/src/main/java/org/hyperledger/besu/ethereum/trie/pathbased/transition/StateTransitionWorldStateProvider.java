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
package org.hyperledger.besu.ethereum.trie.pathbased.transition;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.worldview.VerkleWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.hyperledger.besu.plugin.services.trielogs.StateMigrationLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateTransitionWorldStateProvider implements WorldStateArchive {

  private static final Logger LOG =
      LoggerFactory.getLogger(StateTransitionWorldStateProvider.class);

  private final BonsaiWorldStateProvider bonsaiProvider;
  private final VerkleWorldStateProvider verkleProvider;

  private final long verkleMilestone;
  private final Blockchain blockchain;

  public StateTransitionWorldStateProvider(
      final BonsaiWorldStateProvider bonsaiProvider,
      final VerkleWorldStateProvider verkleProvider,
      final long verkleMilestone,
      final Blockchain blockchain) {
    this.bonsaiProvider = bonsaiProvider;
    this.verkleProvider = verkleProvider;
    this.verkleMilestone = verkleMilestone;
    this.blockchain = blockchain;
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    return verkleProvider
        .get(rootHash, blockHash)
        .or(() -> bonsaiProvider.get(rootHash, blockHash));
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return verkleProvider.isWorldStateAvailable(rootHash, blockHash)
        || bonsaiProvider.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getWorldState(final WorldStateQueryParams params) {
    LOG.debug("Fetching world state for block hash: {}", params.getBlockHash());

    final StateMigrationLog migrationLog =
        verkleProvider
            .getTrieLogManager()
            .getTrieLogLayer(params.getBlockHash())
            .filter(log -> log.getDataStorageFormat() == DataStorageFormat.VERKLE)
            .flatMap(TrieLog::getStateMigrationLog)
            .orElse(new StateMigrationLog(params.getBlockHash(), 5000));

    final BlockHeader bonsaiTarget =
        blockchain.getBlockHeader(migrationLog.getFirstBlockHash()).orElseThrow();
    final Optional<BonsaiWorldState> bonsaiState =
        getBonsaiTransitionState(
            WorldStateQueryParams.newBuilder().from(params).withBlockHeader(bonsaiTarget).build());

    final boolean hasValidBonsaiState =
        bonsaiState
            .map(state -> state.getWorldStateBlockHash().equals(params.getBlockHash()))
            .orElse(false);

    final Optional<VerkleWorldState> verkleState =
        getVerkleTransitionState(params, hasValidBonsaiState, bonsaiTarget);

    final boolean hasValidVerkleState =
        verkleState
            .map(state -> state.getWorldStateBlockHash().equals(params.getBlockHash()))
            .orElse(false);

    if (hasValidBonsaiState || hasValidVerkleState) {
      LOG.info("Matching world state found. Proceeding with state transition.");
      return Optional.of(
          new StateTransitionWorldState(
              bonsaiState.get(),
              verkleState.get(),
              migrationLog,
              hasValidVerkleState,
              verkleMilestone));
    }

    LOG.info("No matching world state found for the requested block hash.");
    return Optional.empty();
  }

  @Override
  public MutableWorldState getWorldState() {
    LOG.debug("Fetching current world state.");

    // Retrieve current Bonsai and Verkle states
    final BonsaiWorldState bonsaiState = (BonsaiWorldState) bonsaiProvider.getWorldState();
    final VerkleWorldState verkleState = (VerkleWorldState) verkleProvider.getWorldState();

    // Retrieve the current chain head block header
    final BlockHeader chainHeadHeader = blockchain.getChainHeadHeader();
    LOG.debug("Current chain head block hash: {}", chainHeadHeader.getBlockHash());

    // Check if Verkle is active based on the milestone timestamp
    final boolean isVerkleActive = chainHeadHeader.getTimestamp() >= verkleMilestone;
    LOG.debug("Verkle active status: {}", isVerkleActive);

    // Retrieve state migration log
    final StateMigrationLog stateMigrationLog =
        verkleProvider
            .getTrieLogManager()
            .getTrieLogLayer(chainHeadHeader.getBlockHash())
            .flatMap(TrieLog::getStateMigrationLog)
            .orElse(new StateMigrationLog(chainHeadHeader.getBlockHash(), 5000));

    LOG.debug(
        "State migration log retrieved for chain head block hash: {}",
        chainHeadHeader.getBlockHash());

    return new StateTransitionWorldState(
        bonsaiState, verkleState, stateMigrationLog, isVerkleActive, verkleMilestone);
  }

  private Optional<BonsaiWorldState> getBonsaiTransitionState(final WorldStateQueryParams params) {
    return bonsaiProvider.getWorldState(params).map(BonsaiWorldState.class::cast);
  }

  private Optional<VerkleWorldState> getVerkleTransitionState(
      final WorldStateQueryParams worldStateQueryParams,
      final boolean hasValidBonsaiState,
      final BlockHeader bonsaiTarget) {
    VerkleWorldState verkleState = (VerkleWorldState) verkleProvider.getWorldState();
    // If the initial state's block hash is zero, it indicates the chain's head
    // has not yet passed the Verkle fork (zero block hash does not exist in a valid chain).
    if (verkleState.getWorldStateBlockHash().isZero()) {
      if (!worldStateQueryParams.shouldWorldStateUpdateHead()) {
        verkleState =
            new VerkleWorldState(
                verkleProvider,
                verkleState.getWorldStateStorage(),
                verkleState.getAccumulator().getEvmConfiguration(),
                verkleProvider.getWorldStateSharedSpec());
        verkleState.freezeStorage();
      }
      if (!hasValidBonsaiState) {
        verkleState.resetWorldStateTo(bonsaiTarget.getBlockHash(), bonsaiTarget.getStateRoot());
        verkleProvider.rollFullWorldStateToBlockHash(
            verkleState, worldStateQueryParams.getBlockHash());
      }
      return Optional.of(verkleState);
    }
    return verkleProvider.getWorldState(worldStateQueryParams).map(VerkleWorldState.class::cast);
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    final boolean isVerkleActive = blockHeader.getTimestamp() >= verkleMilestone;
    if (isVerkleActive) {
      verkleProvider.resetArchiveStateTo(blockHeader);
    } else {
      bonsaiProvider.resetArchiveStateTo(blockHeader);
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
    bonsaiProvider.heal(maybeAccountToRepair, location);
  }

  @Override
  public void close() throws IOException {
    bonsaiProvider.close();
    verkleProvider.close();
  }
}
