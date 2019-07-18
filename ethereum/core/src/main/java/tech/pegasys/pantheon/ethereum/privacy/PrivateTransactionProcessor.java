/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.privacy;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.ProcessableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractMessageProcessor;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.ethereum.vm.Code;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.OperationTracer;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivateTransactionProcessor {

  private static final Logger LOG = LogManager.getLogger();

  @SuppressWarnings("unused")
  private final GasCalculator gasCalculator;

  @SuppressWarnings("unused")
  private final TransactionValidator transactionValidator;

  private final PrivateTransactionValidator privateTransactionValidator;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  private final int maxStackSize;

  private final int createContractAccountVersion;

  public static class Result implements TransactionProcessor.Result {

    private final Status status;

    private final long gasRemaining;

    private final LogSeries logs;

    private final BytesValue output;

    private final ValidationResult<TransactionInvalidReason> validationResult;
    private final Optional<String> revertReason;

    public static Result invalid(
        final ValidationResult<TransactionInvalidReason> validationResult) {
      return new Result(
          Status.INVALID,
          LogSeries.empty(),
          -1,
          BytesValue.EMPTY,
          validationResult,
          Optional.empty());
    }

    public static Result failed(
        final long gasRemaining,
        final ValidationResult<TransactionInvalidReason> validationResult,
        final Optional<String> revertReason) {
      return new Result(
          Status.FAILED,
          LogSeries.empty(),
          gasRemaining,
          BytesValue.EMPTY,
          validationResult,
          revertReason);
    }

    public static Result successful(
        final LogSeries logs,
        final long gasRemaining,
        final BytesValue output,
        final ValidationResult<TransactionInvalidReason> validationResult) {
      return new Result(
          Status.SUCCESSFUL, logs, gasRemaining, output, validationResult, Optional.empty());
    }

    Result(
        final Status status,
        final LogSeries logs,
        final long gasRemaining,
        final BytesValue output,
        final ValidationResult<TransactionInvalidReason> validationResult,
        final Optional<String> revertReason) {
      this.status = status;
      this.logs = logs;
      this.gasRemaining = gasRemaining;
      this.output = output;
      this.validationResult = validationResult;
      this.revertReason = revertReason;
    }

    @Override
    public LogSeries getLogs() {
      return logs;
    }

    @Override
    public long getGasRemaining() {
      return gasRemaining;
    }

    @Override
    public Status getStatus() {
      return status;
    }

    @Override
    public BytesValue getOutput() {
      return output;
    }

    @Override
    public ValidationResult<TransactionInvalidReason> getValidationResult() {
      return validationResult;
    }

    @Override
    public Optional<String> getRevertReason() {
      return revertReason;
    }
  }

  @SuppressWarnings("unused")
  private final boolean clearEmptyAccounts;

  public PrivateTransactionProcessor(
      final GasCalculator gasCalculator,
      final TransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final int createContractAccountVersion) {
    this.gasCalculator = gasCalculator;
    this.transactionValidator = transactionValidator;
    this.privateTransactionValidator = new PrivateTransactionValidator();
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.maxStackSize = maxStackSize;
    this.createContractAccountVersion = createContractAccountVersion;
  }

  @SuppressWarnings("unused")
  public Result processTransaction(
      final Blockchain blockchain,
      final WorldUpdater publicWorldState,
      final WorldUpdater privateWorldState,
      final ProcessableBlockHeader blockHeader,
      final PrivateTransaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final BytesValue privacyGroupId) {
    LOG.trace("Starting private execution of {}", transaction);

    final Address senderAddress = transaction.getSender();
    final MutableAccount maybePrivateSender = privateWorldState.getMutable(senderAddress);
    final MutableAccount sender =
        maybePrivateSender != null
            ? maybePrivateSender
            : privateWorldState.createAccount(senderAddress, 0, Wei.ZERO);

    final ValidationResult<TransactionInvalidReason> validationResult =
        privateTransactionValidator.validate(transaction, sender.getNonce());
    if (!validationResult.isValid()) {
      return Result.invalid(validationResult);
    }

    final long previousNonce = sender.incrementNonce();
    LOG.trace(
        "Incremented private sender {} nonce ({} -> {})",
        senderAddress,
        previousNonce,
        sender.getNonce());

    final MessageFrame initialFrame;
    final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
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
              .blockchain(blockchain)
              .worldState(privateWorldState.updater())
              .address(privateContractAddress)
              .originator(senderAddress)
              .contract(privateContractAddress)
              .contractAccountVersion(createContractAccountVersion)
              .initialGas(Gas.MAX_VALUE)
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
              .maxStackSize(maxStackSize)
              .build();

    } else {
      final Address to = transaction.getTo().get();
      final Account contract = privateWorldState.get(to);

      initialFrame =
          MessageFrame.builder()
              .type(MessageFrame.Type.MESSAGE_CALL)
              .messageFrameStack(messageFrameStack)
              .blockchain(blockchain)
              .worldState(privateWorldState.updater())
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
              .code(new Code(contract != null ? contract.getCode() : BytesValue.EMPTY))
              .blockHeader(blockHeader)
              .depth(0)
              .completer(c -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .maxStackSize(maxStackSize)
              .build();
    }

    messageFrameStack.addFirst(initialFrame);

    while (!messageFrameStack.isEmpty()) {
      process(messageFrameStack.peekFirst(), operationTracer);
    }

    if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      privateWorldState.commit();
    }

    if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
      return Result.successful(
          initialFrame.getLogs(), 0, initialFrame.getOutputData(), ValidationResult.valid());
    } else {
      return Result.failed(
          0,
          ValidationResult.invalid(TransactionInvalidReason.PRIVATE_TRANSACTION_FAILED),
          initialFrame.getRevertReason());
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
