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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.DefaultEvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class PrivateGroupRehydrationBlockProcessor {

  private static final Logger LOG = LogManager.getLogger();

  static final int MAX_GENERATION = 6;

  private final TransactionProcessor transactionProcessor;
  private final PrivateTransactionProcessor privateTransactionProcessor;
  private final MainnetBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  final Wei blockReward;
  private final boolean skipZeroBlockRewards;
  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  public PrivateGroupRehydrationBlockProcessor(
      final TransactionProcessor transactionProcessor,
      final PrivateTransactionProcessor privateTransactionProcessor,
      final MainnetBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards) {
    this.transactionProcessor = transactionProcessor;
    this.privateTransactionProcessor = privateTransactionProcessor;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.blockReward = blockReward;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    this.skipZeroBlockRewards = skipZeroBlockRewards;
  }

  public AbstractBlockProcessor.Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateStorage privateStateStorage,
      final PrivateStateRootResolver privateStateRootResolver,
      final Block block,
      final Map<Hash, PrivateTransaction> forExecution,
      final List<BlockHeader> ommers) {
    long gasUsed = 0;
    final List<TransactionReceipt> receipts = new ArrayList<>();

    final List<Transaction> transactions = block.getBody().getTransactions();
    final BlockHeader blockHeader = block.getHeader();
    for (final Transaction transaction : transactions) {

      final long remainingGasBudget = blockHeader.getGasLimit() - gasUsed;
      if (Long.compareUnsigned(transaction.getGasLimit(), remainingGasBudget) > 0) {
        LOG.warn(
            "Transaction processing error: transaction gas limit {} exceeds available block budget remaining {}",
            transaction.getGasLimit(),
            remainingGasBudget);
        return AbstractBlockProcessor.Result.failed();
      }

      final WorldUpdater worldStateUpdater = worldState.updater();
      final BlockHashLookup blockHashLookup = new BlockHashLookup(blockHeader, blockchain);
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);

      if (forExecution.containsKey(transaction.getHash())) {
        final PrivateTransaction privateTransaction = forExecution.get(transaction.getHash());
        final Hash lastRootHash =
            privateStateRootResolver.resolveLastStateRoot(
                Bytes32.wrap(privateTransaction.getPrivacyGroupId().get()),
                blockHeader.getParentHash());

        final MutableWorldState disposablePrivateState =
            privateWorldStateArchive.getMutable(lastRootHash).get();
        final WorldUpdater privateStateUpdater = disposablePrivateState.updater();
        maybeInjectDefaultManagementAndProxy(
            lastRootHash, disposablePrivateState, privateStateUpdater);
        LOG.debug(
            "Pre-rehydrate root hash: {} for tx {}",
            disposablePrivateState.rootHash(),
            transaction.getHash());

        final PrivateTransactionProcessor.Result privateResult =
            privateTransactionProcessor.processTransaction(
                blockchain,
                worldStateUpdater.updater(),
                privateStateUpdater,
                blockHeader,
                transaction.getHash(),
                privateTransaction,
                miningBeneficiary,
                OperationTracer.NO_TRACING,
                new BlockHashLookup(blockHeader, blockchain),
                privateTransaction.getPrivacyGroupId().get());
        persistPrivateState(
            transaction.getHash(),
            blockHeader.getHash(),
            privateTransaction,
            Bytes32.wrap(privateTransaction.getPrivacyGroupId().get()),
            disposablePrivateState,
            privateStateUpdater,
            privateStateStorage,
            privateResult);
        LOG.debug("Post-rehydrate root hash: {}", disposablePrivateState.rootHash());
      }

      final TransactionProcessor.Result result =
          transactionProcessor.processTransaction(
              blockchain,
              worldStateUpdater,
              blockHeader,
              transaction,
              miningBeneficiary,
              blockHashLookup,
              false,
              TransactionValidationParams.processingBlock());
      if (result.isInvalid()) {
        return AbstractBlockProcessor.Result.failed();
      }

      worldStateUpdater.commit();
      gasUsed = transaction.getGasLimit() - result.getGasRemaining() + gasUsed;
      final TransactionReceipt transactionReceipt =
          transactionReceiptFactory.create(result, worldState, gasUsed);
      receipts.add(transactionReceipt);
    }

    if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
      return AbstractBlockProcessor.Result.failed();
    }

    return AbstractBlockProcessor.Result.successful(receipts);
  }

  protected void persistPrivateState(
      final Hash commitmentHash,
      final Hash currentBlockHash,
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final PrivateStateStorage privateStateStorage,
      final PrivateTransactionProcessor.Result result) {

    LOG.trace(
        "Persisting private state {} for privacyGroup {}",
        disposablePrivateState.rootHash(),
        privacyGroupId);
    privateWorldStateUpdater.commit();
    disposablePrivateState.persist();

    final PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();

    updatePrivateBlockMetadata(
        commitmentHash,
        currentBlockHash,
        privacyGroupId,
        disposablePrivateState.rootHash(),
        privateStateUpdater,
        privateStateStorage);

    final int txStatus =
        result.getStatus() == PrivateTransactionProcessor.Result.Status.SUCCESSFUL ? 1 : 0;

    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(
            txStatus, result.getLogs(), result.getOutput(), result.getRevertReason());

    privateStateUpdater.putTransactionReceipt(
        currentBlockHash, commitmentHash, privateTransactionReceipt);
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage.getPrivacyGroupHeadBlockMap(currentBlockHash).get();
    if (!privacyGroupHeadBlockMap.contains(Bytes32.wrap(privacyGroupId), currentBlockHash)) {
      privacyGroupHeadBlockMap.put(Bytes32.wrap(privacyGroupId), currentBlockHash);
      privateStateUpdater.putPrivacyGroupHeadBlockMap(
          currentBlockHash, new PrivacyGroupHeadBlockMap(privacyGroupHeadBlockMap));
    }
    privateStateUpdater.commit();
  }

  protected void maybeInjectDefaultManagementAndProxy(
      final Hash lastRootHash,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {
    if (lastRootHash.equals(EMPTY_ROOT_HASH)) {
      // inject management
      final DefaultEvmAccount managementPrecompile =
          privateWorldStateUpdater.createAccount(Address.DEFAULT_ONCHAIN_PRIVACY_MANAGEMENT);
      final MutableAccount mutableManagementPrecompiled = managementPrecompile.getMutable();
      // this is the code for the simple management contract
      mutableManagementPrecompiled.setCode(
          OnChainGroupManagement.DEFAULT_GROUP_MANAGEMENT_RUNTIME_BYTECODE);

      // inject proxy
      final DefaultEvmAccount proxyPrecompile =
          privateWorldStateUpdater.createAccount(Address.ONCHAIN_PRIVACY_PROXY);
      final MutableAccount mutableProxyPrecompiled = proxyPrecompile.getMutable();
      // this is the code for the proxy contract
      mutableProxyPrecompiled.setCode(OnChainGroupManagement.PROXY_RUNTIME_BYTECODE);
      // manually set the management contract address so the proxy can trust it
      mutableProxyPrecompiled.setStorageValue(
          UInt256.ZERO,
          UInt256.fromBytes(Bytes32.leftPad(Address.DEFAULT_ONCHAIN_PRIVACY_MANAGEMENT)));

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist();
    }
  }

  private void updatePrivateBlockMetadata(
      final Hash markerTransactionHash,
      final Hash currentBlockHash,
      final Bytes32 privacyGroupId,
      final Hash rootHash,
      final PrivateStateStorage.Updater privateStateUpdater,
      final PrivateStateStorage privateStateStorage) {
    final PrivateBlockMetadata privateBlockMetadata =
        privateStateStorage
            .getPrivateBlockMetadata(currentBlockHash, Bytes32.wrap(privacyGroupId))
            .orElseGet(PrivateBlockMetadata::empty);
    privateBlockMetadata.addPrivateTransactionMetadata(
        new PrivateTransactionMetadata(markerTransactionHash, rootHash));
    privateStateUpdater.putPrivateBlockMetadata(
        Bytes32.wrap(currentBlockHash), Bytes32.wrap(privacyGroupId), privateBlockMetadata);
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
    final MutableAccount coinbase = updater.getOrCreate(header.getCoinbase()).getMutable();

    coinbase.incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.warn(
            "Block processing error: ommer block number {} more than {} generations current block number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final MutableAccount ommerCoinbase =
          updater.getOrCreate(ommerHeader.getCoinbase()).getMutable();
      final long distance = header.getNumber() - ommerHeader.getNumber();
      final Wei ommerReward = blockReward.subtract(blockReward.multiply(distance).divide(8));
      ommerCoinbase.incrementBalance(ommerReward);
    }

    return true;
  }
}
