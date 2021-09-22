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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MergeBlockProcessor extends MainnetBlockProcessor {
  private static final Logger LOG = LogManager.getLogger();
  private final MergeContext mergeContext;

  public MergeBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final Optional<GoQuorumPrivacyParameters> goQuorumPrivacyParameters) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        goQuorumPrivacyParameters);
    this.mergeContext = MergeContextFactory.get();
  }

  @Override
  protected boolean rewardCoinbase(
      final MutableWorldState worldState,
      final BlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {

    if (!mergeContext.isPostMerge(header)) {
      super.rewardCoinbase(worldState, header, ommers, skipZeroBlockRewards);
    }
    // do not issue block rewards post-merge
    return true;
  }

  /**
   * This is a post-merge specific method to execute a block using the BlockProcessor, but not
   * commit.
   */
  public CandidateBlock executeBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers,
      final PrivateMetadataUpdater privateMetadataUpdater) {

    CandidateUpdateWrapper candidateUpdater =
        new CandidateUpdateWrapper(worldState.updater(), blockHeader.getHash());

    CompletableFuture<Result> result =
        CompletableFuture.supplyAsync(
                () ->
                    super.processBlock(
                        blockchain,
                        worldState,
                        candidateUpdater,
                        blockHeader,
                        transactions,
                        ommers,
                        privateMetadataUpdater))
            // fail any block that takes longer than a slot to execute:
            .orTimeout(12000, TimeUnit.MILLISECONDS);

    return new CandidateBlock(blockHeader.getHash(), result, candidateUpdater);
  }

  public static class CandidateBlock {
    final Hash blockhash;
    final CompletableFuture<? extends BlockProcessor.Result> result;
    final CandidateUpdateWrapper candidateUpdateWrapper;

    CandidateBlock(
        final Hash blockhash,
        final CompletableFuture<? extends BlockProcessor.Result> result,
        final CandidateUpdateWrapper candidateUpdater) {
      this.blockhash = blockhash;
      this.result = result;
      this.candidateUpdateWrapper = candidateUpdater;
    }

    public CompletableFuture<? extends BlockProcessor.Result> getResult() {
      return result;
    }

    public Hash getBlockhash() {
      return blockhash;
    }

    public void setConsensusValidated() {
      candidateUpdateWrapper.setConsensusValidated();
    }
  }

  static class CandidateUpdateWrapper implements WorldUpdater {
    final WorldUpdater wrappedUpdater;
    final Hash blockhash;

    final Object commitLock = new Object();
    final AtomicBoolean consensusValidated = new AtomicBoolean(false);
    final AtomicBoolean blockExecuted = new AtomicBoolean(false);

    CandidateUpdateWrapper(final WorldUpdater updater, final Hash blockhash) {
      this.wrappedUpdater = updater;
      this.blockhash = blockhash;
    }

    /**
     * Set the candidate block hash as consensus validated. Attempts to commit if block has
     * completed executing.
     */
    public void setConsensusValidated() {
      synchronized (commitLock) {
        this.consensusValidated.set(true);
      }
      // attempt to commit
      commit();
    }

    /**
     * Set the candidate block hash as executed. Attempts to commit if block has been marked
     * consensus validated.
     */
    public void setBlockExecuted() {
      synchronized (commitLock) {
        this.blockExecuted.set(true);
      }
      // attempt to commit
      commit();
    }

    @Override
    public WorldUpdater updater() {
      return wrappedUpdater.updater();
    }

    @Override
    public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
      return wrappedUpdater.createAccount(address, nonce, balance);
    }

    @Override
    public EvmAccount getAccount(final Address address) {
      return wrappedUpdater.getAccount(address);
    }

    @Override
    public void deleteAccount(final Address address) {
      wrappedUpdater.deleteAccount(address);
    }

    @Override
    public Collection<? extends Account> getTouchedAccounts() {
      return wrappedUpdater.getTouchedAccounts();
    }

    @Override
    public Collection<Address> getDeletedAccountAddresses() {
      return wrappedUpdater.getDeletedAccountAddresses();
    }

    @Override
    public void revert() {
      wrappedUpdater.revert();
    }

    /**
     * Commit the underlying wrapped WorldState IF consensus has been validated for it. Thread safe
     */
    @Override
    public void commit() {

      boolean shouldCommit = false;
      synchronized (commitLock) {
        // synchronize to ensure we do not have a race that prevents committing
        shouldCommit = blockExecuted.get() && consensusValidated.get();
      }

      if (shouldCommit) {
        wrappedUpdater.commit();
        LOG.trace("Committed blockhash {}: consensus validated}", blockhash.toShortHexString());
      } else {
        LOG.trace(
            "Uncommitted blockhash {}: blockExecuted: {}; consensusValidated {}",
            blockhash.toShortHexString(),
            blockExecuted.get(),
            consensusValidated.get());
      }
    }

    @Override
    public Optional<WorldUpdater> parentUpdater() {
      return Optional.empty();
    }

    @Override
    public Account get(final Address address) {
      return wrappedUpdater.get(address);
    }
  }
}
