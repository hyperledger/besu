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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainnetTransactionProcessor implements TransactionProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final GasCalculator gasCalculator;

  private final TransactionValidator transactionValidator;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  private final int maxStackSize;

  private final int createContractAccountVersion;

  public static class Result implements TransactionProcessor.Result {

    private final Status status;

    private final long gasRemaining;

    private final LogSeries logs;

    private final BytesValue output;

    private final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult;
    private final Optional<BytesValue> revertReason;

    public static Result invalid(
        final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult) {
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
        final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult,
        final Optional<BytesValue> revertReason) {
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
        final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult) {
      return new Result(
          Status.SUCCESSFUL, logs, gasRemaining, output, validationResult, Optional.empty());
    }

    Result(
        final Status status,
        final LogSeries logs,
        final long gasRemaining,
        final BytesValue output,
        final ValidationResult<TransactionValidator.TransactionInvalidReason> validationResult,
        final Optional<BytesValue> revertReason) {
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
    public ValidationResult<TransactionValidator.TransactionInvalidReason> getValidationResult() {
      return validationResult;
    }

    @Override
    public Optional<BytesValue> getRevertReason() {
      return revertReason;
    }
  }

  private final boolean clearEmptyAccounts;

  public MainnetTransactionProcessor(
      final GasCalculator gasCalculator,
      final TransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final int createContractAccountVersion) {
    this.gasCalculator = gasCalculator;
    this.transactionValidator = transactionValidator;
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.maxStackSize = maxStackSize;
    this.createContractAccountVersion = createContractAccountVersion;
  }

  @Override
  public Result processTransaction(
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

      initialFrame =
          MessageFrame.builder()
              .type(MessageFrame.Type.CONTRACT_CREATION)
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

    messageFrameStack.addFirst(initialFrame);

    while (!messageFrameStack.isEmpty()) {
      process(messageFrameStack.peekFirst(), operationTracer);
    }

    if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
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

  private static Gas refunded(
      final Transaction transaction, final Gas gasRemaining, final Gas gasRefund) {
    // Integer truncation takes care of the the floor calculation needed after the divide.
    final Gas maxRefundAllowance =
        Gas.of(transaction.getGasLimit()).minus(gasRemaining).dividedBy(2);
    final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
    return gasRemaining.plus(refundAllowance);
  }
}
