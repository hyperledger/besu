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
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

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

  /**
   * This is a post-merge specific method to execute a block using the BlockProcessor, but not
   * commit.
   *
   * @param blockchain a MutableBlockchain
   * @param worldState a MutableWorldState
   * @param blockHeader blockHeader to execute
   * @param transactions list of Transaction to execute
   * @return CandidateBlock response.
   */
  public CandidateBlock executeBlock(
      final MutableBlockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions) {

    CandidateWorldState candidateWorldState = new CandidateWorldState(worldState, blockHeader);

    final CandidateBlock candidateBlock =
        new CandidateBlock(
            new Block(blockHeader, new BlockBody(transactions, Collections.emptyList())),
            candidateWorldState,
            blockchain);

    // TODO: this setting happening here makes me think we actually might have the invariant in the
    // code base right now where a block is only a candidate if it's been sucessfully executed. That
    // means we don't need to do this setting anywhere and can flush a candidate as soon as we get a
    // consensusValidated. Especially since setConsensusValidated is a noop if there isn't a
    // candidate block already
    candidateBlock.setBlockProcessorResult(
        super.executeBlock(
            blockchain,
            candidateWorldState,
            blockHeader,
            transactions,
            Collections.emptyList(), // no ommers for merge blocks
            null));

    return candidateBlock;
  }

  public static class CandidateBlock {
    final Block block;
    final Optional<CandidateWorldState> candidateWorldState;
    final MutableBlockchain blockchain;

    final Object commitLock = new Object();

    final AtomicBoolean consensusValidated = new AtomicBoolean(false);
    final AtomicReference<BlockProcessor.Result> blockProcessorResult = new AtomicReference<>();

    public CandidateBlock(
        final Block block,
        final CandidateWorldState candidateWorldState,
        final MutableBlockchain blockchain) {
      this.block = block;
      this.candidateWorldState = Optional.ofNullable(candidateWorldState);
      this.blockchain = blockchain;
    }

    public AtomicReference<BlockProcessor.Result> getBlockProcessorResult() {
      return blockProcessorResult;
    }

    public Hash getBlockHash() {
      return block.getHash();
    }

    /**
     * Set the candidate block hash as consensus validated. Attempts to commit if block has
     * completed executing.
     */
    public void setConsensusValidated() {
      synchronized (commitLock) {
        this.consensusValidated.set(true);
      }
      maybePersistChainAndStateData();
    }

    private void setBlockProcessorResult(final BlockProcessor.Result blockProcessorResult) {
      synchronized (commitLock) {
        this.blockProcessorResult.set(blockProcessorResult);
      }
      maybePersistChainAndStateData();
    }

    private void maybePersistChainAndStateData() {

      final boolean shouldCommit;
      synchronized (commitLock) {
        // synchronize to ensure we do not have a race that prevents committing
        shouldCommit =
            consensusValidated.get()
                && Optional.ofNullable(blockProcessorResult.get())
                    .map(BlockProcessor.Result::isSuccessful)
                    .orElse(false);
      }

      if (shouldCommit) {
        candidateWorldState.ifPresent(ws -> ws.flush());
        blockchain.appendBlock(block, getBlockProcessorResult().get().getReceipts());
        LOG.debug(
            "Committed blockhash {}: consensus validated}", getBlockHash().toShortHexString());
      } else {
        LOG.debug(
            "Uncommitted blockhash {}: blockExecuted: {}; consensusValidated {}",
            getBlockHash().toShortHexString(),
            Optional.ofNullable(blockProcessorResult.get())
                .map(BlockProcessor.Result::isSuccessful)
                .orElse(false),
            consensusValidated.get());
      }
    }

    /**
     * update the blockchain head and fetch the specified new finalized block. We do both atomically
     * to ensure we do not get head and finalized on different forks.
     *
     * @param headBlockHash hash of new head block.
     * @param finalizedBlockHash hash of new finalized block.
     * @return BlockHeader of finalized block on success.
     */
    public Optional<BlockHeader> updateForkChoice(
        final Hash headBlockHash, final Hash finalizedBlockHash) {

      final Optional<Block> newFinalized = blockchain.getBlockByHash(finalizedBlockHash);
      if (newFinalized.isEmpty() && !finalizedBlockHash.equals(Hash.ZERO)) {
        // we should only fail to find when it's the special value 0x000..000
        throw new IllegalStateException(
            String.format(
                "should've been able to find block hash %s but couldn't", finalizedBlockHash));
      }

      // ensure we have headBlock:
      Block newHead =
          blockchain
              .getBlockByHash(headBlockHash)
              .orElseGet(
                  () -> {
                    Optional<Block> flushedBlock = Optional.empty();
                    // if new head is candidateBlock, flush and do not wait on consensusValidated:
                    if (getBlockHash().equals(headBlockHash)) {
                      candidateWorldState.ifPresent(ws -> ws.flush());
                      blockchain.appendBlock(block, getBlockProcessorResult().get().getReceipts());
                      flushedBlock = blockchain.getBlockByHash(headBlockHash);
                    }
                    // if we still can't find it, throw.
                    return flushedBlock.orElseThrow();
                  });

      // TODO: ensure head is a descendant of finalized!
      // set the new head
      blockchain.rewindToBlock(newHead.getHash());
      return newFinalized.map(Block::getHeader);
    }

    @Override
    public String toString() {
      return String.format(
          "CandidateBlock{block=%s, candidateWorldState=%s, blockchain=%s}",
          block, candidateWorldState, blockchain);
    }
  }

  static class CandidateWorldState implements MutableWorldState {
    final MutableWorldState worldState;
    final BlockHeader blockHeader;

    CandidateWorldState(final MutableWorldState worldState, final BlockHeader blockHeader) {
      this.worldState = worldState;
      this.blockHeader = blockHeader;
    }

    public void flush() {
      worldState.persist(blockHeader);
    }

    @Override
    public MutableWorldState copy() {
      return worldState.copy();
    }

    @Override
    public void persist(final BlockHeader blockHeader) {
      worldState.persist(blockHeader);
    }

    @Override
    public Hash rootHash() {
      return worldState.rootHash();
    }

    @Override
    public Hash frontierRootHash() {
      return worldState.frontierRootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
      return worldState.streamAccounts(startKeyHash, limit);
    }

    @Override
    public WorldUpdater updater() {
      return worldState.updater();
    }

    @Override
    public Account get(final Address address) {
      return worldState.get(address);
    }
  }
}
