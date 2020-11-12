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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.AbstractMessageProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.operations.ReturnStack;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutablePrivateWorldStateUpdater;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PrivateTransactionProcessor {

  private static final Logger LOG = LogManager.getLogger();

  @SuppressWarnings("unused")
  private final GasCalculator gasCalculator;

  @SuppressWarnings("unused")
  private final MainnetTransactionValidator transactionValidator;

  private final PrivateTransactionValidator privateTransactionValidator;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  private final int maxStackSize;

  private final int createContractAccountVersion;

  @SuppressWarnings("unused")
  private final boolean clearEmptyAccounts;

  public PrivateTransactionProcessor(
      final GasCalculator gasCalculator,
      final MainnetTransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final int createContractAccountVersion,
      final PrivateTransactionValidator privateTransactionValidator) {
    this.gasCalculator = gasCalculator;
    this.transactionValidator = transactionValidator;
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.maxStackSize = maxStackSize;
    this.createContractAccountVersion = createContractAccountVersion;
    this.privateTransactionValidator = privateTransactionValidator;
  }

  @SuppressWarnings("unused")
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater publicWorldState,
      final WorldUpdater privateWorldState,
      final ProcessableBlockHeader blockHeader,
      final Hash pmtHash,
      final PrivateTransaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Bytes privacyGroupId) {
    try {
      LOG.trace("Starting private execution of {}", transaction);

      final Address senderAddress = transaction.getSender();
      final EvmAccount maybePrivateSender = privateWorldState.getAccount(senderAddress);
      final MutableAccount sender =
          maybePrivateSender != null
              ? maybePrivateSender.getMutable()
              : privateWorldState.createAccount(senderAddress, 0, Wei.ZERO).getMutable();

      final ValidationResult<TransactionInvalidReason> validationResult =
          privateTransactionValidator.validate(transaction, sender.getNonce(), false);
      if (!validationResult.isValid()) {
        return TransactionProcessingResult.invalid(validationResult);
      }

      final long previousNonce = sender.incrementNonce();
      LOG.trace(
          "Incremented private sender {} nonce ({} -> {})",
          senderAddress,
          previousNonce,
          sender.getNonce());

      final MessageFrame initialFrame;
      final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

      final WorldUpdater mutablePrivateWorldStateUpdater =
          new DefaultMutablePrivateWorldStateUpdater(publicWorldState, privateWorldState);

      final ReturnStack returnStack = new ReturnStack();

      if (transaction.isContractCreation()) {
        final Address privateContractAddress =
            Address.privateContractAddress(senderAddress, previousNonce, privacyGroupId);

        LOG.debug(
            "Calculated contract address {} from sender {} with nonce {} and privacy group {}",
            privateContractAddress.toString(),
            senderAddress,
            previousNonce,
            privacyGroupId.toString());

        initialFrame =
            MessageFrame.builder()
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .messageFrameStack(messageFrameStack)
                .returnStack(returnStack)
                .blockchain(blockchain)
                .worldState(mutablePrivateWorldStateUpdater)
                .address(privateContractAddress)
                .originator(senderAddress)
                .contract(privateContractAddress)
                .contractAccountVersion(createContractAccountVersion)
                .initialGas(Gas.MAX_VALUE)
                .gasPrice(transaction.getGasPrice())
                .inputData(Bytes.EMPTY)
                .sender(senderAddress)
                .value(transaction.getValue())
                .apparentValue(transaction.getValue())
                .code(new Code(transaction.getPayload()))
                .blockHeader(blockHeader)
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(miningBeneficiary)
                .blockHashLookup(blockHashLookup)
                .maxStackSize(maxStackSize)
                .transactionHash(pmtHash)
                .build();

      } else {
        final Address to = transaction.getTo().get();
        final Account contract = privateWorldState.get(to);

        initialFrame =
            MessageFrame.builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .messageFrameStack(messageFrameStack)
                .returnStack(returnStack)
                .blockchain(blockchain)
                .worldState(mutablePrivateWorldStateUpdater)
                .address(to)
                .originator(senderAddress)
                .contract(to)
                .contractAccountVersion(
                    contract != null ? contract.getVersion() : Account.DEFAULT_VERSION)
                .initialGas(Gas.MAX_VALUE)
                .gasPrice(transaction.getGasPrice())
                .inputData(transaction.getPayload())
                .sender(senderAddress)
                .value(transaction.getValue())
                .apparentValue(transaction.getValue())
                .code(new Code(contract != null ? contract.getCode() : Bytes.EMPTY))
                .blockHeader(blockHeader)
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(miningBeneficiary)
                .blockHashLookup(blockHashLookup)
                .maxStackSize(maxStackSize)
                .transactionHash(pmtHash)
                .build();
      }

      messageFrameStack.addFirst(initialFrame);

      while (!messageFrameStack.isEmpty()) {
        process(messageFrameStack.peekFirst(), operationTracer);
      }

      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        mutablePrivateWorldStateUpdater.commit();
      }

      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        return TransactionProcessingResult.successful(
            initialFrame.getLogs(), 0, 0, initialFrame.getOutputData(), ValidationResult.valid());
      } else {
        return TransactionProcessingResult.failed(
            0,
            0,
            ValidationResult.invalid(TransactionInvalidReason.PRIVATE_TRANSACTION_FAILED),
            initialFrame.getRevertReason());
      }
    } catch (final RuntimeException re) {
      LOG.error("Critical Exception Processing Transaction", re);
      return TransactionProcessingResult.invalid(
          ValidationResult.invalid(
              TransactionInvalidReason.INTERNAL_ERROR,
              "Internal Error in Besu - " + re.toString()));
    }
  }

  @SuppressWarnings("unused")
  private static void clearEmptyAccounts(final WorldUpdater worldState) {
    worldState.getTouchedAccounts().stream()
        .filter(Account::isEmpty)
        .forEach(a -> worldState.deleteAccount(a.getAddress()));
  }

  private void process(final MessageFrame frame, final OperationTracer operationTracer) {
    final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());

    executor.process(frame, operationTracer);
  }

  private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
    switch (type) {
      case MESSAGE_CALL:
        return messageCallProcessor;
      case CONTRACT_CREATION:
        return contractCreationProcessor;
      default:
        throw new IllegalStateException("Request for unsupported message processor type " + type);
    }
  }

  @SuppressWarnings("unused")
  private static Gas refunded(
      final Transaction transaction, final Gas gasRemaining, final Gas gasRefund) {
    // Integer truncation takes care of the the floor calculation needed after the divide.
    final Gas maxRefundAllowance =
        Gas.of(transaction.getGasLimit()).minus(gasRemaining).dividedBy(2);
    final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
    return gasRemaining.plus(refundAllowance);
  }
}
