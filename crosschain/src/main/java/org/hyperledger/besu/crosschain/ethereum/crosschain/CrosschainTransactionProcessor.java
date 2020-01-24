/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crosschain.ethereum.crosschain;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.CrosschainTransaction;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.AbstractMessageProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrosschainTransactionProcessor extends MainnetTransactionProcessor {
  private static final Logger LOG = LogManager.getLogger();

  public CrosschainTransactionProcessor(
      final GasCalculator gasCalculator,
      final TransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final int createContractAccountVersion) {
    super(
        gasCalculator,
        transactionValidator,
        contractCreationProcessor,
        messageCallProcessor,
        clearEmptyAccounts,
        maxStackSize,
        createContractAccountVersion);
  }

  @Override
  public TransactionProcessor.Result processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingState,
      final TransactionValidationParams transactionValidationParams) {
    LOG.trace("Starting execution of {}", transaction);

    ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult =
        transactionValidator.validate(transaction);
    // Make sure the transaction is intrinsically valid before trying to
    // compare against a sender account (because the transaction may not
    // be signed correctly to extract the sender).
    if (!validationResult.isValid()) {
      LOG.warn("Invalid transaction: {}", validationResult.getErrorMessage());
      return Result.invalid(validationResult);
    }

    final Address senderAddress = transaction.getSender();
    LOG.info("Sender address: {}", senderAddress);
    final MutableAccount sender = worldState.getOrCreate(senderAddress);
    validationResult =
        transactionValidator.validateForSender(transaction, sender, transactionValidationParams);
    if (!validationResult.isValid()) {
      LOG.warn("Invalid transaction: {}", validationResult.getErrorMessage());
      return Result.invalid(validationResult);
    }

    final long previousNonce = sender.incrementNonce();
    LOG.trace(
        "Incremented sender {} nonce ({} -> {})", senderAddress, previousNonce, sender.getNonce());

    final Wei upfrontGasCost = transaction.getUpfrontGasCost();
    final Wei previousBalance = sender.decrementBalance(upfrontGasCost);
    LOG.trace(
        "Deducted sender {} upfront gas cost {} ({} -> {})",
        senderAddress,
        upfrontGasCost,
        previousBalance,
        sender.getBalance());

    final Gas intrinsicGas = gasCalculator.transactionIntrinsicGasCost(transaction);
    final Gas gasAvailable = Gas.of(transaction.getGasLimit()).minus(intrinsicGas);
    LOG.trace(
        "Gas available for execution {} = {} - {} (limit - intrinsic)",
        gasAvailable,
        transaction.getGasLimit(),
        intrinsicGas);

    final WorldUpdater worldUpdater = worldState.updater();
    final MessageFrame initialFrame;
    final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
    if (transaction.isContractCreation()) {
      final Address contractAddress =
          Address.contractAddress(senderAddress, sender.getNonce() - 1L);

      MessageFrame.Type contractCreationType = MessageFrame.Type.CONTRACT_CREATION;
      if ((transaction instanceof CrosschainTransaction)
          && ((CrosschainTransaction) transaction).getType().isLockableContractDeploy()) {
        contractCreationType = MessageFrame.Type.CONTRACT_CREATION_LOCKABLE_CONTRACT;
        LOG.info("Lockable contract deploy: New Contract Address: {}", contractAddress.toString());
      }

      initialFrame =
          MessageFrame.builder()
              .type(contractCreationType)
              .messageFrameStack(messageFrameStack)
              .blockchain(blockchain)
              .worldState(worldUpdater.updater())
              .initialGas(gasAvailable)
              .address(contractAddress)
              .originator(senderAddress)
              .contract(contractAddress)
              .contractAccountVersion(createContractAccountVersion)
              .gasPrice(transaction.getGasPrice())
              .inputData(BytesValue.EMPTY)
              .sender(senderAddress)
              .value(transaction.getValue())
              .apparentValue(transaction.getValue())
              .code(new Code(transaction.getPayload()))
              .blockHeader(blockHeader)
              .depth(0)
              .completer(c -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .isPersistingState(isPersistingState)
              .maxStackSize(maxStackSize)
              .build();

    } else {
      final Address to = transaction.getTo().get();
      final Account contract = worldState.get(to);

      if (transaction instanceof CrosschainTransaction) {
        CrosschainTransaction.CrosschainTransactionType type =
            ((CrosschainTransaction) transaction).getType();
        if (type.isSignallingTransaction()) {
          boolean commit = type.isUnlockCommitSignallingTransaction();
          final MutableAccount mutableContract = worldState.getMutable(contract.getAddress());
          mutableContract.unlock(commit);
          worldUpdater.commit();
          LOG.debug(
              "Signalling Transaction: unlock_{} for account {}",
              (commit ? "commit" : "ignore"),
              contract.getAddress());
          return Result.successful(LogSeries.empty(), 0, BytesValue.EMPTY, validationResult);
        }
      }

      initialFrame =
          MessageFrame.builder()
              .type(MessageFrame.Type.MESSAGE_CALL)
              .messageFrameStack(messageFrameStack)
              .blockchain(blockchain)
              .worldState(worldUpdater.updater())
              .initialGas(gasAvailable)
              .address(to)
              .originator(senderAddress)
              .contract(to)
              .contractAccountVersion(
                  contract != null ? contract.getVersion() : Account.DEFAULT_VERSION)
              .gasPrice(transaction.getGasPrice())
              .inputData(transaction.getPayload())
              .sender(senderAddress)
              .value(transaction.getValue())
              .apparentValue(transaction.getValue())
              .code(new Code(contract != null ? contract.getCode() : BytesValue.EMPTY))
              .blockHeader(blockHeader)
              .depth(0)
              .completer(c -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .maxStackSize(maxStackSize)
              .isPersistingState(isPersistingState)
              .build();
    }

    // If we are processing a crosschain transaction, then add the transaction context such that
    // the precompile can access it.
    if (transaction instanceof CrosschainTransaction) {
      // Add to thread local storage.
      CrosschainThreadLocalDataHolder.setCrosschainTransaction((CrosschainTransaction) transaction);
      // Rewind to the first subordinate transaction or view for each execution.
      ((CrosschainTransaction) transaction).resetSubordinateTransactionsAndViewsList();
    } else {
      CrosschainThreadLocalDataHolder.setTransaction(transaction);
    }

    messageFrameStack.addFirst(initialFrame);

    while (!messageFrameStack.isEmpty()) {
      process(messageFrameStack.peekFirst(), operationTracer);
    }

    // Clean-up steps for crosschain transactions post execution:
    // Check that all of the subordinate transactions and views were used in the transaction
    // execution.
    // Remove the transaction context from thread local storage.
    if (transaction instanceof CrosschainTransaction) {
      if (((CrosschainTransaction) transaction).getNextSubordinateTransactionOrView() != null) {
        LOG.error(
            "Crosschain transaction ended prior to all Subordinate Transactions and Views being consumed.");
        initialFrame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
      }
      CrosschainThreadLocalDataHolder.removeCrosschainTransaction();
    }

    if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      // Fail if a locked account is touched + lock touched accounts.
      Collection<Account> changedAccounts = worldUpdater.getTouchedAccounts();
      for (Account acc : changedAccounts) {
        Address accAddress = acc.getAddress();
        if (acc.isLockable()) {
          if (acc.isLocked()) {
            LOG.error(
                "Attempt to execute transaction on locked contract: {}", accAddress.toString());
            return Result.failed(gasAvailable.toLong(), validationResult, null);
          }

          // Normal, non-crosschain transactions do not lock contracts.
          if (transaction instanceof CrosschainTransaction) {
            LOG.info("Locking updated account: {}", accAddress.toString());
            final MutableAccount mutableContract = worldUpdater.getMutable(accAddress);
            mutableContract.lock();
          }
        } else {
          // Normal non-crosschain transactions can change non-lockable contract.
          if (transaction instanceof CrosschainTransaction) {
            CrosschainTransaction xTx = (CrosschainTransaction) transaction;
            if (xTx.getType().isLockableTransaction()) {
              if (acc.hasCode()) {
                LOG.error(
                    "Attempt to execute crosschain transaction on non-lockable contract: {}",
                    accAddress.toString());
                return Result.failed(gasAvailable.toLong(), validationResult, null);
              } else {
                // The account that has changed is an EOA or a precompile.
                // The sender account must increase its nonce as part of a transaction.
                // However, no other account can change, and the sender's balance at this point
                // should
                // remain unchanged. That is, they shouldn't transfer Ether into a locked account or
                // have Ether sent from a locked account. This is to prevent Ether being created or
                // destroyed as a result of ignored Crosschain Transactions.
                if (accAddress.equals(senderAddress)) {
                  if (!acc.getBalance().equals(previousBalance.plus(upfrontGasCost))) {
                    LOG.error(
                        "Sender account has sent or received Ether as part of a Crosschain Transaction: {}",
                        acc.getAddress().toString());
                    return Result.failed(gasAvailable.toLong(), validationResult, null);
                  }
                } else {
                  if (accAddress.isPrecompile()) {
                    LOG.info(
                        "*****Updated precompile account {}: Needs further investigation",
                        accAddress.toString());
                  } else {
                    LOG.error(
                        "****Attempt to alter EOA account other than sender: {}",
                        acc.getAddress().toString());
                    return Result.failed(gasAvailable.toLong(), validationResult, null);
                  }
                }
              }
            }
          }
        }
      }

      //      Collection<Address> deletedAccounts = worldUpdater.deletedAccounts();
      //      for (Address addr: deletedAccounts) {
      //        // TODO: We will have to work out how to handle deleted accounts in the future.
      //        // TODO: calling the code below will return null if the account has been deleted.
      //        //Account acc = worldState.get(addr);
      //
      //      }

      worldUpdater.commit();
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Gas used by transaction: {}, by message call/contract creation: {}",
          () -> Gas.of(transaction.getGasLimit()).minus(initialFrame.getRemainingGas()),
          () -> gasAvailable.minus(initialFrame.getRemainingGas()));
    }

    // Refund the sender by what we should and pay the miner fee (note that we're doing them one
    // after the other so that if it is the same account somehow, we end up with the right result)
    final Gas selfDestructRefund =
        gasCalculator.getSelfDestructRefundAmount().times(initialFrame.getSelfDestructs().size());
    final Gas refundGas = initialFrame.getGasRefund().plus(selfDestructRefund);
    final Gas refunded = refunded(transaction, initialFrame.getRemainingGas(), refundGas);
    final Wei refundedWei = refunded.priceFor(transaction.getGasPrice());
    sender.incrementBalance(refundedWei);

    final MutableAccount coinbase = worldState.getOrCreate(miningBeneficiary);
    final Gas coinbaseFee = Gas.of(transaction.getGasLimit()).minus(refunded);
    final Wei coinbaseWei = coinbaseFee.priceFor(transaction.getGasPrice());
    coinbase.incrementBalance(coinbaseWei);

    initialFrame.getSelfDestructs().forEach(worldState::deleteAccount);

    if (clearEmptyAccounts) {
      clearEmptyAccounts(worldState);
    }

    if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      return Result.successful(
          initialFrame.getLogs(),
          refunded.toLong(),
          initialFrame.getOutputData(),
          validationResult);
    } else {
      return Result.failed(refunded.toLong(), validationResult, initialFrame.getRevertReason());
    }
  }

  @Override
  protected AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
    switch (type) {
      case MESSAGE_CALL:
        return messageCallProcessor;
      case CONTRACT_CREATION:
      case CONTRACT_CREATION_LOCKABLE_CONTRACT:
        return contractCreationProcessor;
      default:
        throw new IllegalStateException("Request for unsupported message processor type " + type);
    }
  }
}
