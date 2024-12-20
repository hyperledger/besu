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
package org.hyperledger.besu.ethereum.privacy;

import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateGroupRehydrationBlockProcessor {

  private static final Logger LOG =
      LoggerFactory.getLogger(PrivateGroupRehydrationBlockProcessor.class);

  static final int MAX_GENERATION = 6;

  private final MainnetTransactionProcessor transactionProcessor;
  private final PrivateTransactionProcessor privateTransactionProcessor;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  final Wei blockReward;
  private final boolean skipZeroBlockRewards;
  private final PrivateStateGenesisAllocator privateStateGenesisAllocator;
  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  public PrivateGroupRehydrationBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final PrivateTransactionProcessor privateTransactionProcessor,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final PrivateStateGenesisAllocator privateStateGenesisAllocator) {
    this.transactionProcessor = transactionProcessor;
    this.privateTransactionProcessor = privateTransactionProcessor;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.blockReward = blockReward;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    this.skipZeroBlockRewards = skipZeroBlockRewards;
    this.privateStateGenesisAllocator = privateStateGenesisAllocator;
  }

  public BlockProcessingResult processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateStorage privateStateStorage,
      final PrivateStateRootResolver privateStateRootResolver,
      final Block block,
      final BlockHashLookup blockHashLookup,
      final Map<Hash, PrivateTransaction> forExecution,
      final List<BlockHeader> ommers) {
    long gasUsed = 0;
    final List<TransactionReceipt> receipts = new ArrayList<>();

    final List<Transaction> transactions = block.getBody().getTransactions();
    final BlockHeader blockHeader = block.getHeader();
    final PrivateMetadataUpdater metadataUpdater =
        new PrivateMetadataUpdater(blockHeader, privateStateStorage);

    for (final Transaction transaction : transactions) {

      final long remainingGasBudget = blockHeader.getGasLimit() - gasUsed;
      if (Long.compareUnsigned(transaction.getGasLimit(), remainingGasBudget) > 0) {
        LOG.warn(
            "Transaction processing error: transaction gas limit {} exceeds available block budget"
                + " remaining {}",
            transaction.getGasLimit(),
            remainingGasBudget);
        return BlockProcessingResult.FAILED;
      }

      final WorldUpdater worldStateUpdater = worldState.updater();
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);

      final Hash transactionHash = transaction.getHash();
      if (forExecution.containsKey(transactionHash)) {
        final PrivateTransaction privateTransaction = forExecution.get(transactionHash);
        final Bytes32 privacyGroupId = Bytes32.wrap(privateTransaction.getPrivacyGroupId().get());
        final Hash lastRootHash =
            privateStateRootResolver.resolveLastStateRoot(privacyGroupId, metadataUpdater);

        final MutableWorldState disposablePrivateState =
            privateWorldStateArchive.getMutable(lastRootHash, null).get();
        final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

        if (lastRootHash.equals(EMPTY_ROOT_HASH)) {
          privateStateGenesisAllocator.applyGenesisToPrivateWorldState(
              disposablePrivateState,
              privateWorldStateUpdater,
              privacyGroupId,
              blockHeader.getNumber());
        }

        LOG.debug(
            "Pre-rehydrate root hash: {} for tx {}",
            disposablePrivateState.rootHash(),
            transactionHash);

        final TransactionProcessingResult privateResult =
            privateTransactionProcessor.processTransaction(
                worldStateUpdater.updater(),
                privateWorldStateUpdater,
                blockHeader,
                transactionHash,
                privateTransaction,
                miningBeneficiary,
                OperationTracer.NO_TRACING,
                blockHashLookup,
                privateTransaction.getPrivacyGroupId().get());

        privateWorldStateUpdater.commit();
        disposablePrivateState.persist(null);

        storePrivateMetadata(
            transactionHash,
            privacyGroupId,
            disposablePrivateState,
            metadataUpdater,
            privateResult);

        LOG.debug("Post-rehydrate root hash: {}", disposablePrivateState.rootHash());
      }

      // We have to process the public transactions here, because the private transactions can
      // depend on  public state
      final TransactionProcessingResult result =
          transactionProcessor.processTransaction(
              worldStateUpdater,
              blockHeader,
              transaction,
              miningBeneficiary,
              blockHashLookup,
              false,
              TransactionValidationParams.processingBlock(),
              Wei.ZERO);
      if (result.isInvalid()) {
        return BlockProcessingResult.FAILED;
      }

      gasUsed = transaction.getGasLimit() - result.getGasRemaining() + gasUsed;
      final TransactionReceipt transactionReceipt =
          transactionReceiptFactory.create(transaction.getType(), result, worldState, gasUsed);
      receipts.add(transactionReceipt);
    }

    if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
      return BlockProcessingResult.FAILED;
    }

    metadataUpdater.commit();
    BlockProcessingOutputs blockProcessingOutput = new BlockProcessingOutputs(worldState, receipts);
    return new BlockProcessingResult(Optional.of(blockProcessingOutput));
  }

  void storePrivateMetadata(
      final Hash commitmentHash,
      final Bytes32 privacyGroupId,
      final MutableWorldState disposablePrivateState,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final TransactionProcessingResult result) {

    final int txStatus =
        result.getStatus() == TransactionProcessingResult.Status.SUCCESSFUL ? 1 : 0;

    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(
            txStatus, result.getLogs(), result.getOutput(), result.getRevertReason());

    privateMetadataUpdater.putTransactionReceipt(commitmentHash, privateTransactionReceipt);
    privateMetadataUpdater.updatePrivacyGroupHeadBlockMap(privacyGroupId);
    privateMetadataUpdater.addPrivateTransactionMetadata(
        privacyGroupId,
        new PrivateTransactionMetadata(commitmentHash, disposablePrivateState.rootHash()));
  }

  private boolean rewardCoinbase(
      final MutableWorldState worldState,
      final ProcessableBlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {
    if (skipZeroBlockRewards && blockReward.isZero()) {
      return true;
    }

    final Wei coinbaseReward = blockReward.add(blockReward.multiply(ommers.size()).divide(32));
    final WorldUpdater updater = worldState.updater();
    final MutableAccount coinbase = updater.getOrCreate(header.getCoinbase());

    coinbase.incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.warn(
            "Block processing error: ommer block number {} more than {} generations current block"
                + " number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final MutableAccount ommerCoinbase = updater.getOrCreate(ommerHeader.getCoinbase());
      final long distance = header.getNumber() - ommerHeader.getNumber();
      final Wei ommerReward = blockReward.subtract(blockReward.multiply(distance).divide(8));
      ommerCoinbase.incrementBalance(ommerReward);
    }

    return true;
  }
}
