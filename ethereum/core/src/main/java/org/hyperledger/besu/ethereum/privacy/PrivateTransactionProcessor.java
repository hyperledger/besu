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

import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION_HASH;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidatorFactory;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateTransactionProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(PrivateTransactionProcessor.class);

  @SuppressWarnings("unused")
  private final TransactionValidatorFactory transactionValidatorFactory;

  private final PrivateTransactionValidator privateTransactionValidator;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  private final int maxStackSize;

  @SuppressWarnings("unused")
  private final boolean clearEmptyAccounts;

  public PrivateTransactionProcessor(
      final TransactionValidatorFactory transactionValidatorFactory,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final PrivateTransactionValidator privateTransactionValidator) {
    this.transactionValidatorFactory = transactionValidatorFactory;
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.maxStackSize = maxStackSize;
    this.privateTransactionValidator = privateTransactionValidator;
  }

  public TransactionProcessingResult processTransaction(
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
      final MutableAccount maybePrivateSender = privateWorldState.getAccount(senderAddress);
      final MutableAccount sender =
          maybePrivateSender != null
              ? maybePrivateSender
              : privateWorldState.createAccount(senderAddress, 0, Wei.ZERO);

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

      final WorldUpdater mutablePrivateWorldStateUpdater =
          new PrivateMutableWorldStateUpdater(publicWorldState, privateWorldState);
      final MessageFrame.Builder commonMessageFrameBuilder =
          MessageFrame.builder()
              .maxStackSize(maxStackSize)
              .worldUpdater(mutablePrivateWorldStateUpdater)
              .initialGas(Long.MAX_VALUE)
              .originator(senderAddress)
              .gasPrice(transaction.getGasPrice())
              .sender(senderAddress)
              .value(transaction.getValue())
              .apparentValue(transaction.getValue())
              .blockValues(blockHeader)
              .completer(__ -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .contextVariables(Map.of(KEY_TRANSACTION_HASH, pmtHash));

      final MessageFrame initialFrame;
      if (transaction.isContractCreation()) {
        final Address privateContractAddress =
            Address.privateContractAddress(senderAddress, previousNonce, privacyGroupId);

        LOG.debug(
            "Calculated contract address {} from sender {} with nonce {} and privacy group {}",
            privateContractAddress,
            senderAddress,
            previousNonce,
            privacyGroupId);

        final Bytes initCodeBytes = transaction.getPayload();
        Code code = contractCreationProcessor.getCodeFromEVMForCreation(initCodeBytes);
        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .address(privateContractAddress)
                .contract(privateContractAddress)
                .inputData(initCodeBytes.slice(code.getSize()))
                .code(code)
                .build();
      } else {
        final Address to = transaction.getTo().get();
        final Optional<Account> maybeContract =
            Optional.ofNullable(mutablePrivateWorldStateUpdater.get(to));
        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(transaction.getPayload())
                .code(
                    maybeContract
                        .map(c -> messageCallProcessor.getCodeFromEVM(c.getCodeHash(), c.getCode()))
                        .orElse(CodeV0.EMPTY_CODE))
                .build();
      }

      final Deque<MessageFrame> messageFrameStack = initialFrame.getMessageFrameStack();
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
              TransactionInvalidReason.INTERNAL_ERROR, "Internal Error in Besu - " + re));
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
    return switch (type) {
      case MESSAGE_CALL -> messageCallProcessor;
      case CONTRACT_CREATION -> contractCreationProcessor;
    };
  }
}
