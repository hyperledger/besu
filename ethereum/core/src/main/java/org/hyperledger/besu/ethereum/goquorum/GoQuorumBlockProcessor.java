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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.GoQuorumPrivacyParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.worldstate.GoQuorumMutablePrivateWorldStateUpdater;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoQuorumBlockProcessor extends MainnetBlockProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(GoQuorumBlockProcessor.class);

  private final GoQuorumEnclave goQuorumEnclave;
  private final GoQuorumPrivateStorage goQuorumPrivateStorage;

  public GoQuorumBlockProcessor(
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
        Optional.empty());

    this.goQuorumEnclave = goQuorumPrivacyParameters.orElseThrow().enclave();
    this.goQuorumPrivateStorage = goQuorumPrivacyParameters.orElseThrow().privateStorage();
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState publicWorldState,
      final MutableWorldState privateWorldState,
      final Block block) {
    final BlockHeader blockHeader = block.getHeader();
    final List<Transaction> transactions = block.getBody().getTransactions();
    final List<BlockHeader> ommers = block.getBody().getOmmers();

    final List<TransactionReceipt> publicTxReceipts = new ArrayList<>();
    final List<TransactionReceipt> privateTxReceipts = new ArrayList<>();
    long currentGasUsed = 0;

    final GoQuorumPrivateStorage.Updater privateStorageUpdater = goQuorumPrivateStorage.updater();

    for (final Transaction transaction : transactions) {
      if (!hasAvailableBlockBudget(blockHeader, transaction, currentGasUsed)) {
        return AbstractBlockProcessor.Result.failed();
      }

      final WorldUpdater publicWorldStateUpdater = publicWorldState.updater();
      final BlockHashLookup blockHashLookup = new BlockHashLookup(blockHeader, blockchain);
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);

      WorldUpdater effectiveWorldUpdater = null;
      Transaction effectiveTransaction;

      final boolean isGoQuorumPrivateTransaction =
          transaction.isGoQuorumPrivateTransaction(
              transactionProcessor.getTransactionValidator().getGoQuorumCompatibilityMode());

      if (isGoQuorumPrivateTransaction) { // private transaction
        try {
          effectiveTransaction = retrievePrivateTransactionFromEnclave(transaction);

          effectiveWorldUpdater =
              new GoQuorumMutablePrivateWorldStateUpdater(
                  publicWorldStateUpdater, privateWorldState.updater());

        } catch (final EnclaveClientException e) { // private transaction but not party to it
          // We do not have to execute anything, but we still need to validate the transaction
          effectiveTransaction = null;
          final ValidationResult<TransactionInvalidReason> validationResult =
              validateTransaction(blockHeader, transaction, publicWorldStateUpdater);
          if (!validationResult.isValid()) {
            return AbstractBlockProcessor.Result.failed();
          }
        }
      } else { // public Transaction
        effectiveWorldUpdater = publicWorldState.updater();

        effectiveTransaction = transaction;
      }

      if (effectiveTransaction != null) { // public tx, or private tx that we are party to
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
                TransactionValidationParams.processingBlock(),
                null);

        if (result.isInvalid()) {
          LOG.info(
              "Block processing error: transaction invalid '{}'. Block {} Transaction {}",
              result.getValidationResult().getInvalidReason(),
              blockHeader.getHash().toHexString(),
              transaction.getHash().toHexString());
          return AbstractBlockProcessor.Result.failed();
        }

        if (isGoQuorumPrivateTransaction) { // private transaction we are party to
          publicTxReceipts.add(
              transactionReceiptFactory.create(
                  transaction.getType(),
                  publicResultForWhenWeHaveAPrivateTransaction(transaction),
                  publicWorldState,
                  currentGasUsed));
          privateTxReceipts.add(
              transactionReceiptFactory.create(
                  transaction.getType(), result, privateWorldState, currentGasUsed));
          publicWorldStateUpdater
              .getOrCreate(effectiveTransaction.getSender())
              .getMutable()
              .incrementNonce();
          effectiveWorldUpdater.commit();
        } else { // public transaction
          final long gasUsed = transaction.getGasLimit() - result.getGasRemaining();
          currentGasUsed += gasUsed;

          publicTxReceipts.add(
              transactionReceiptFactory.create(
                  transaction.getType(), result, publicWorldState, currentGasUsed));
          privateTxReceipts.add(null);
          effectiveWorldUpdater.commit();
        }
      } else { // private transaction we are not party to
        publicTxReceipts.add(
            transactionReceiptFactory.create(
                transaction.getType(),
                publicResultForWhenWeHaveAPrivateTransaction(transaction),
                publicWorldState,
                currentGasUsed));
        privateTxReceipts.add(null);
        publicWorldStateUpdater.getOrCreate(transaction.getSender()).getMutable().incrementNonce();
      }

      publicWorldStateUpdater.commit();
    }

    if (!rewardCoinbase(publicWorldState, blockHeader, ommers, skipZeroBlockRewards)) {
      // no need to log, rewardCoinbase logs the error.
      return AbstractBlockProcessor.Result.failed();
    }

    // create the bloom for the private transactions in the block and store it
    final LogsBloomFilter.Builder privateBloomBuilder = LogsBloomFilter.builder();
    privateTxReceipts.stream()
        .filter(pr -> pr != null)
        .forEach(pr -> privateBloomBuilder.insertFilter(pr.getBloomFilter()));
    blockHeader.setPrivateLogsBloom(privateBloomBuilder.build());

    publicWorldState.persist(blockHeader);
    privateWorldState.persist(null);

    privateStorageUpdater.putPrivateStateRootHashMapping(
        publicWorldState.rootHash(), privateWorldState.rootHash());
    privateStorageUpdater.commit();

    return Result.successful(publicTxReceipts, privateTxReceipts);
  }

  private ValidationResult<TransactionInvalidReason> validateTransaction(
      final BlockHeader blockHeader,
      final Transaction transaction,
      final WorldUpdater publicWorldStateUpdater) {
    final MainnetTransactionValidator transactionValidator =
        transactionProcessor.getTransactionValidator();
    final TransactionValidationParams transactionValidationParams =
        TransactionValidationParams.processingBlock();
    ValidationResult<TransactionInvalidReason> validationResult =
        transactionValidator.validate(
            transaction, blockHeader.getBaseFee(), transactionValidationParams);
    if (!validationResult.isValid()) {
      LOG.warn(
          "Invalid transaction: {}. Block {} Transaction {}",
          validationResult.getErrorMessage(),
          blockHeader.getHash().toHexString(),
          transaction.getHash().toHexString());
      return validationResult;
    }

    final Address senderAddress = transaction.getSender();

    final EvmAccount sender = publicWorldStateUpdater.getOrCreate(senderAddress);
    validationResult =
        transactionValidator.validateForSender(transaction, sender, transactionValidationParams);
    if (!validationResult.isValid()) {
      LOG.warn(
          "Invalid transaction: {}. Block {} Transaction {}",
          validationResult.getErrorMessage(),
          blockHeader.getHash().toHexString(),
          transaction.getHash().toHexString());
      return validationResult;
    }
    return ValidationResult.valid();
  }

  private TransactionProcessingResult publicResultForWhenWeHaveAPrivateTransaction(
      final Transaction transaction) {
    return TransactionProcessingResult.successful(
        Collections.emptyList(),
        0,
        transaction.getGasLimit(),
        Bytes.EMPTY,
        ValidationResult.valid());
  }

  private Transaction retrievePrivateTransactionFromEnclave(final Transaction transaction) {
    final GoQuorumReceiveResponse receive =
        goQuorumEnclave.receive(transaction.getPayload().toBase64String());

    final Bytes privatePayload = Bytes.wrap(receive.getPayload());

    return new Transaction(
        transaction.getNonce(),
        transaction.getGasPrice().get(),
        transaction.getGasLimit(),
        transaction.getTo(),
        transaction.getValue(),
        transaction.getSignature(),
        privatePayload,
        transaction
            .getSender(), // at this point we are checking the signature of the public transaction
        // and we are setting the sender for the private transaction, so the
        // signature of the private transaction will not (and should not) be
        // checked again.
        transaction.getChainId(),
        Optional.of(transaction.getV()));
  }
}
