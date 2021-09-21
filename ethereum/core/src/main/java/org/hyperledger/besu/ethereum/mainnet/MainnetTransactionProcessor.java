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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.AccessListEntry;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.GoQuorumMutablePrivateWorldStateUpdater;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class MainnetTransactionProcessor {

  private static final Logger LOG = LogManager.getLogger();

  protected final GasCalculator gasCalculator;

  protected final MainnetTransactionValidator transactionValidator;

  private final AbstractMessageProcessor contractCreationProcessor;

  private final AbstractMessageProcessor messageCallProcessor;

  protected final int maxStackSize;

  protected final boolean clearEmptyAccounts;

  protected final FeeMarket feeMarket;
  protected final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator;

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     MainnetTransactionValidator}
   * @return the transaction result
   * @see MainnetTransactionValidator
   * @see TransactionValidationParams
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        OperationTracer.NO_TRACING,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams Validation parameters that will be used by the {@link
   *     MainnetTransactionValidator}
   * @param operationTracer operation tracer {@link OperationTracer}
   * @return the transaction result
   * @see MainnetTransactionValidator
   * @see TransactionValidationParams
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param operationTracer The tracer to record results of each EVM operation
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @return the transaction result
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        ImmutableTransactionValidationParams.builder().build(),
        null);
  }

  /**
   * Applies a transaction to the current system state.
   *
   * @param blockchain The current blockchain
   * @param worldState The current world state
   * @param blockHeader The current block header
   * @param transaction The transaction to process
   * @param operationTracer The tracer to record results of each EVM operation
   * @param miningBeneficiary The address which is to receive the transaction fee
   * @param blockHashLookup The {@link BlockHashLookup} to use for BLOCKHASH operations
   * @param isPersistingPrivateState Whether the resulting private state will be persisted
   * @param transactionValidationParams The transaction validation parameters to use
   * @return the transaction result
   */
  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams) {
    return processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        miningBeneficiary,
        operationTracer,
        blockHashLookup,
        isPersistingPrivateState,
        transactionValidationParams,
        null);
  }

  public MainnetTransactionProcessor(
      final GasCalculator gasCalculator,
      final MainnetTransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final FeeMarket feeMarket,
      final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator) {
    this.gasCalculator = gasCalculator;
    this.transactionValidator = transactionValidator;
    this.contractCreationProcessor = contractCreationProcessor;
    this.messageCallProcessor = messageCallProcessor;
    this.clearEmptyAccounts = clearEmptyAccounts;
    this.maxStackSize = maxStackSize;
    this.feeMarket = feeMarket;
    this.coinbaseFeePriceCalculator = coinbaseFeePriceCalculator;
  }

  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater worldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final Boolean isPersistingPrivateState,
      final TransactionValidationParams transactionValidationParams,
      final PrivateMetadataUpdater privateMetadataUpdater) {
    try {
      LOG.trace("Starting execution of {}", transaction);
      ValidationResult<TransactionInvalidReason> validationResult =
          transactionValidator.validate(
              transaction, blockHeader.getBaseFee(), transactionValidationParams);
      // Make sure the transaction is intrinsically valid before trying to
      // compare against a sender account (because the transaction may not
      // be signed correctly to extract the sender).
      if (!validationResult.isValid()) {
        LOG.warn("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final Address senderAddress = transaction.getSender();

      final EvmAccount sender = worldState.getOrCreateSenderAccount(senderAddress);

      validationResult =
          transactionValidator.validateForSender(transaction, sender, transactionValidationParams);
      if (!validationResult.isValid()) {
        LOG.debug("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final var senderMutableAccount = sender.getMutable();
      final long previousNonce = senderMutableAccount.incrementNonce();
      final Wei transactionGasPrice =
          feeMarket.getTransactionPriceCalculator().price(transaction, blockHeader.getBaseFee());
      LOG.trace(
          "Incremented sender {} nonce ({} -> {})",
          senderAddress,
          previousNonce,
          sender.getNonce());

      final Wei upfrontGasCost = transaction.getUpfrontGasCost(transactionGasPrice);
      final Wei previousBalance = senderMutableAccount.decrementBalance(upfrontGasCost);
      LOG.trace(
          "Deducted sender {} upfront gas cost {} ({} -> {})",
          senderAddress,
          upfrontGasCost,
          previousBalance,
          sender.getBalance());

      List<AccessListEntry> accessListEntries = transaction.getAccessList().orElse(List.of());
      // we need to keep a separate hash set of addresses in case they specify no storage.
      // No-storage is a common pattern, especially for Externally Owned Accounts
      Set<Address> addressList = new HashSet<>();
      Multimap<Address, Bytes32> storageList = HashMultimap.create();
      int accessListStorageCount = 0;
      for (var entry : accessListEntries) {
        Address address = entry.getAddress();
        addressList.add(address);
        List<Bytes32> storageKeys = entry.getStorageKeys();
        storageList.putAll(address, storageKeys);
        accessListStorageCount += storageKeys.size();
      }

      final Gas intrinsicGas =
          gasCalculator.transactionIntrinsicGasCost(
              transaction.getPayload(), transaction.isContractCreation());
      final Gas accessListGas =
          gasCalculator.accessListGasCost(accessListEntries.size(), accessListStorageCount);
      final Gas gasAvailable =
          Gas.of(transaction.getGasLimit()).minus(intrinsicGas).minus(accessListGas);
      LOG.trace(
          "Gas available for execution {} = {} - {} (limit - intrinsic)",
          gasAvailable,
          transaction.getGasLimit(),
          intrinsicGas);

      final WorldUpdater worldUpdater = worldState.updater();
      final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
      final MessageFrame.Builder commonMessageFrameBuilder =
          MessageFrame.builder()
              .messageFrameStack(messageFrameStack)
              .maxStackSize(maxStackSize)
              .blockchain(blockchain)
              .worldState(worldUpdater.updater())
              .initialGas(gasAvailable)
              .originator(senderAddress)
              .gasPrice(transactionGasPrice)
              .sender(senderAddress)
              .value(transaction.getValue())
              .apparentValue(transaction.getValue())
              .blockHeader(blockHeader)
              .depth(0)
              .completer(__ -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .isPersistingPrivateState(isPersistingPrivateState)
              .transactionHash(transaction.getHash())
              .transaction(transaction)
              .accessListWarmAddresses(addressList)
              .accessListWarmStorage(storageList)
              .privateMetadataUpdater(privateMetadataUpdater);

      final MessageFrame initialFrame;
      if (transaction.isContractCreation()) {
        final Address contractAddress =
            Address.contractAddress(senderAddress, senderMutableAccount.getNonce() - 1L);

        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .address(contractAddress)
                .contract(contractAddress)
                .inputData(Bytes.EMPTY)
                .code(new Code(transaction.getPayload()))
                .build();
      } else {
        @SuppressWarnings("OptionalGetWithoutIsPresent") // isContractCall tests isPresent
        final Address to = transaction.getTo().get();
        final Optional<Account> maybeAccount = Optional.ofNullable(worldState.get(to));
        final Optional<Code> maybeContract =
            maybeAccount.isPresent()
                ? worldState.getContract(maybeAccount.get())
                : Optional.empty();
        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .inputData(transaction.getPayload())
                .code(maybeContract.orElse(new Code(Bytes.EMPTY)))
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
      final Wei refundedWei = refunded.priceFor(transactionGasPrice);
      senderMutableAccount.incrementBalance(refundedWei);

      final Gas gasUsedByTransaction =
          Gas.of(transaction.getGasLimit()).minus(initialFrame.getRemainingGas());

      if (!worldState.getClass().equals(GoQuorumMutablePrivateWorldStateUpdater.class)) {
        // if this is not a private GoQuorum transaction we have to update the coinbase
        final var coinbase = worldState.getOrCreate(miningBeneficiary).getMutable();
        final Gas coinbaseFee = Gas.of(transaction.getGasLimit()).minus(refunded);
        if (blockHeader.getBaseFee().isPresent()) {
          final Wei baseFee = Wei.of(blockHeader.getBaseFee().get());
          if (transactionGasPrice.compareTo(baseFee) < 0) {
            return TransactionProcessingResult.failed(
                gasUsedByTransaction.toLong(),
                refunded.toLong(),
                ValidationResult.invalid(
                    TransactionInvalidReason.TRANSACTION_PRICE_TOO_LOW,
                    "transaction price must be greater than base fee"),
                Optional.empty());
          }
        }
        final CoinbaseFeePriceCalculator coinbaseCalculator =
            blockHeader.getBaseFee().isPresent()
                ? coinbaseFeePriceCalculator
                : CoinbaseFeePriceCalculator.frontier();
        final Wei coinbaseWeiDelta =
            coinbaseCalculator.price(coinbaseFee, transactionGasPrice, blockHeader.getBaseFee());

        coinbase.incrementBalance(coinbaseWeiDelta);
      }

      initialFrame.getSelfDestructs().forEach(worldState::deleteAccount);

      if (clearEmptyAccounts) {
        clearEmptyAccounts(worldState);
      }

      if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        return TransactionProcessingResult.successful(
            initialFrame.getLogs(),
            gasUsedByTransaction.toLong(),
            refunded.toLong(),
            initialFrame.getOutputData(),
            validationResult);
      } else {
        return TransactionProcessingResult.failed(
            gasUsedByTransaction.toLong(),
            refunded.toLong(),
            validationResult,
            initialFrame.getRevertReason());
      }
    } catch (final RuntimeException re) {
      LOG.error("Critical Exception Processing Transaction", re);
      return TransactionProcessingResult.invalid(
          ValidationResult.invalid(
              TransactionInvalidReason.INTERNAL_ERROR, "Internal Error in Besu - " + re));
    }
  }

  public MainnetTransactionValidator getTransactionValidator() {
    return transactionValidator;
  }

  protected static void clearEmptyAccounts(final WorldUpdater worldState) {
    new ArrayList<>(worldState.getTouchedAccounts())
        .stream().filter(Account::isEmpty).forEach(a -> worldState.deleteAccount(a.getAddress()));
  }

  protected void process(final MessageFrame frame, final OperationTracer operationTracer) {
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

  protected Gas refunded(
      final Transaction transaction, final Gas gasRemaining, final Gas gasRefund) {
    // Integer truncation takes care of the the floor calculation needed after the divide.
    final Gas maxRefundAllowance =
        Gas.of(transaction.getGasLimit())
            .minus(gasRemaining)
            .dividedBy(gasCalculator.getMaxRefundQuotient());
    final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
    return gasRemaining.plus(refundAllowance);
  }
}
