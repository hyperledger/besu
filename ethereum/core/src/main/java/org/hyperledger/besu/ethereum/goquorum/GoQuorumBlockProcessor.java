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
package org.hyperledger.besu.ethereum.goquorum;

import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutablePrivateWorldStateUpdater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class GoQuorumBlockProcessor extends MainnetBlockProcessor {

  private static final Logger LOG = LogManager.getLogger();

  // TODO-goquorum proper wiring instead of static references?
  private final GoQuorumEnclave goQuorumEnclave;
  private final GoQuorumPrivateStorage goQuorumPrivateStorage;

  public GoQuorumBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final TransactionGasBudgetCalculator gasBudgetCalculator) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        gasBudgetCalculator);

    this.goQuorumEnclave = GoQuorumPrivacyParameters.goQuorumEnclave;
    this.goQuorumPrivateStorage = GoQuorumKeyValueStorage.INSTANCE;
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final MutableWorldState privateWorldState,
      final Block block) {
    final BlockHeader blockHeader = block.getHeader();
    final List<Transaction> transactions = block.getBody().getTransactions();
    final List<BlockHeader> ommers = block.getBody().getOmmers();

    final List<TransactionReceipt> publicTxReceipts = new ArrayList<>();
    long currentGasUsed = 0;

    final GoQuorumPrivateStorage.Updater privateStorageUpdater = goQuorumPrivateStorage.updater();

    for (final Transaction transaction : transactions) {
      if (!hasAvailableBlockBudget(blockHeader, transaction, currentGasUsed)) {
        return AbstractBlockProcessor.Result.failed();
      }

      final WorldUpdater publicWorldStateUpdater = worldState.updater();
      final BlockHashLookup blockHashLookup = new BlockHashLookup(blockHeader, blockchain);
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);

      final WorldUpdater effectiveWorldUpdater;
      final Transaction effectiveTransaction;
      if (transaction.isGoQuorumPrivateTransaction()) {
        effectiveWorldUpdater =
            new DefaultMutablePrivateWorldStateUpdater(
                publicWorldStateUpdater, privateWorldState.updater());

        effectiveTransaction = retrievePrivateTransactionFromEnclave(transaction);
      } else {
        effectiveWorldUpdater = worldState.updater();
        effectiveTransaction = transaction;
      }

      final TransactionProcessingResult result =
          transactionProcessor.processTransaction(
              blockchain,
              effectiveWorldUpdater,
              blockHeader,
              effectiveTransaction,
              miningBeneficiary,
              OperationTracer.NO_TRACING,
              blockHashLookup,
              true,
              TransactionValidationParams.processingBlock());

      if (result.isInvalid()) {
        LOG.info(
            "Block processing error: transaction invalid '{}'. Block {} Transaction {}",
            result.getValidationResult().getInvalidReason(),
            blockHeader.getHash().toHexString(),
            transaction.getHash().toHexString());
        return AbstractBlockProcessor.Result.failed();
      } else {
        // TODO-goquorum We need to increment the sender's public nonce. Ss there a better way of
        // doing this??
        publicWorldStateUpdater.getAccount(transaction.getSender()).getMutable().incrementNonce();
      }

      effectiveWorldUpdater.commit();
      publicWorldStateUpdater.commit();

      currentGasUsed += transaction.getGasLimit() - result.getGasRemaining();

      if (transaction.isGoQuorumPrivateTransaction()) {
        // Only the logs are used for the Public transaction receipt
        final TransactionProcessingResult publicResult =
            TransactionProcessingResult.successful(
                Collections.emptyList(),
                0,
                result.getGasRemaining(),
                Bytes.EMPTY,
                result.getValidationResult());

        publicTxReceipts.add(
            transactionReceiptFactory.create(transaction.getType(), publicResult, worldState, 0));

        final TransactionReceipt privateTransactionReceipt =
            transactionReceiptFactory.create(
                transaction.getType(), result, privateWorldState, currentGasUsed);
        privateStorageUpdater.putTransactionReceipt(
            blockHeader.getHash(), transaction.getHash(), privateTransactionReceipt);
      } else {
        publicTxReceipts.add(
            transactionReceiptFactory.create(
                transaction.getType(), result, worldState, currentGasUsed));
      }
    }

    if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
      // no need to log, rewardCoinbase logs the error.
      return AbstractBlockProcessor.Result.failed();
    }

    worldState.persist(blockHeader);
    privateWorldState.persist(null);

    privateStorageUpdater.putPrivateStateRootHashMapping(
        worldState.rootHash(), privateWorldState.rootHash());
    privateStorageUpdater.commit();

    return Result.successful(publicTxReceipts);
  }

  private Transaction retrievePrivateTransactionFromEnclave(final Transaction transaction) {
    // TODO-goquorum we don't need the enclave key (after Tessera is updated)
    final GoQuorumReceiveResponse receive =
        goQuorumEnclave.receive(
            transaction.getPayload().toBase64String(), GoQuorumPrivacyParameters.enclaveKey);

    final Bytes privatePayload = Bytes.wrap(receive.getPayload());

    return new Transaction(
        transaction.getNonce(),
        transaction.getGasPrice(),
        transaction.getGasLimit(),
        transaction.getTo(),
        transaction.getValue(),
        transaction.getSignature(),
        privatePayload,
        transaction.getSender(),
        transaction.getChainId(),
        Optional.of(transaction.getV()));
  }
}
