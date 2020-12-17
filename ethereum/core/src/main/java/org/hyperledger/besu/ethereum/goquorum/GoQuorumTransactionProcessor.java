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

import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.GoQuorumEnclave;
import org.hyperledger.besu.enclave.types.GoQuorumReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.AccountState;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.AbstractMessageProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.operations.ReturnStack;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class GoQuorumTransactionProcessor extends MainnetTransactionProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final GoQuorumEnclave goQuorumEnclave;

  public GoQuorumTransactionProcessor(
      final GasCalculator gasCalculator,
      final MainnetTransactionValidator transactionValidator,
      final AbstractMessageProcessor contractCreationProcessor,
      final AbstractMessageProcessor messageCallProcessor,
      final boolean clearEmptyAccounts,
      final int maxStackSize,
      final int createContractAccountVersion,
      final TransactionPriceCalculator transactionPriceCalculator,
      final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator) {
    super(
        gasCalculator,
        transactionValidator,
        contractCreationProcessor,
        messageCallProcessor,
        clearEmptyAccounts,
        maxStackSize,
        createContractAccountVersion,
        transactionPriceCalculator,
        coinbaseFeePriceCalculator);

    // TODO-goquorum proper enclave wiring
    this.goQuorumEnclave =
        new EnclaveFactory(Vertx.vertx())
            .createGoQuorumEnclave(URI.create("http://localhost:8888/receive"));
  }

  public TransactionProcessingResult processTransaction(
      final Blockchain blockchain,
      final WorldUpdater publicWorldState,
      final WorldUpdater privateWorldState,
      final ProcessableBlockHeader blockHeader,
      final Transaction transaction,
      final Address miningBeneficiary,
      final OperationTracer operationTracer,
      final BlockHashLookup blockHashLookup,
      final TransactionValidationParams transactionValidationParams) {
    try {
      LOG.trace("Starting execution of {}", transaction);

      // TODO-goquorum we don't need the enclave key
      final GoQuorumReceiveResponse receive =
          goQuorumEnclave.receive(
              transaction.getPayload().toBase64String(),
              "6Myia17XP6v/UGZu6KaXQPqo2jSbvJTPxFtBSzjD5iU=");
      final Bytes privatePayload = Bytes.wrap(receive.getPayload());

      final Transaction quorumTx =
          new Transaction(
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

      // TODO-goquorum can we reduce duplication?

      ValidationResult<TransactionInvalidReason> validationResult =
          transactionValidator.validate(quorumTx, blockHeader.getBaseFee());
      // Make sure the transaction is intrinsically valid before trying to
      // compare against a sender account (because the transaction may not
      // be signed correctly to extract the sender).
      if (!validationResult.isValid()) {
        LOG.warn("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final Address senderAddress = quorumTx.getSender();
      final EvmAccount sender = publicWorldState.getOrCreate(senderAddress);

      // initialize in private world state account
      privateWorldState.getOrCreate(senderAddress);

      validationResult =
          transactionValidator.validateForSender(quorumTx, sender, transactionValidationParams);
      if (!validationResult.isValid()) {
        LOG.debug("Invalid transaction: {}", validationResult.getErrorMessage());
        return TransactionProcessingResult.invalid(validationResult);
      }

      final MutableAccount senderMutableAccount = sender.getMutable();
      final long previousNonce = senderMutableAccount.incrementNonce();
      final Wei transactionGasPrice =
          transactionPriceCalculator.price(quorumTx, blockHeader.getBaseFee());
      LOG.trace(
          "Incremented sender {} nonce ({} -> {})",
          senderAddress,
          previousNonce,
          sender.getNonce());

      final Wei upfrontGasCost = quorumTx.getUpfrontGasCost(transactionGasPrice);
      final Wei previousBalance = senderMutableAccount.decrementBalance(upfrontGasCost);
      LOG.trace(
          "Deducted sender {} upfront gas cost {} ({} -> {})",
          senderAddress,
          upfrontGasCost,
          previousBalance,
          sender.getBalance());

      final Gas intrinsicGas = gasCalculator.transactionIntrinsicGasCost(quorumTx);
      final Gas gasAvailable = Gas.of(quorumTx.getGasLimit()).minus(intrinsicGas);
      LOG.trace(
          "Gas available for execution {} = {} - {} (limit - intrinsic)",
          gasAvailable,
          quorumTx.getGasLimit(),
          intrinsicGas);

      final WorldUpdater worldUpdater = privateWorldState.updater();
      final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
      final MessageFrame.Builder commonMessageFrameBuilder =
          MessageFrame.builder()
              .messageFrameStack(messageFrameStack)
              .maxStackSize(maxStackSize)
              .returnStack(new ReturnStack())
              .blockchain(blockchain)
              // TODO-goquorum Should we have a reference to a read-only public world state (so
              // private tx can read from public state)? How will we enforce read-only operations? -
              // I think we solved this for Besu Privacy, how?
              .worldState(worldUpdater.updater())
              .initialGas(gasAvailable)
              .originator(senderAddress)
              .gasPrice(transactionGasPrice)
              .sender(senderAddress)
              .value(quorumTx.getValue())
              .apparentValue(quorumTx.getValue())
              .blockHeader(blockHeader)
              .depth(0)
              .completer(__ -> {})
              .miningBeneficiary(miningBeneficiary)
              .blockHashLookup(blockHashLookup)
              .transactionHash(quorumTx.getHash());

      final MessageFrame initialFrame;
      if (quorumTx.isContractCreation()) {
        final Address contractAddress =
            Address.contractAddress(senderAddress, sender.getNonce() - 1L);

        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .address(contractAddress)
                .contract(contractAddress)
                .contractAccountVersion(createContractAccountVersion)
                .inputData(Bytes.EMPTY)
                .code(new Code(quorumTx.getPayload()))
                .build();
      } else {
        final Address to = quorumTx.getTo().get();
        final Optional<Account> maybeContract = Optional.ofNullable(privateWorldState.get(to));
        initialFrame =
            commonMessageFrameBuilder
                .type(MessageFrame.Type.MESSAGE_CALL)
                .address(to)
                .contract(to)
                .contractAccountVersion(
                    maybeContract.map(AccountState::getVersion).orElse(Account.DEFAULT_VERSION))
                .inputData(quorumTx.getPayload())
                .code(new Code(maybeContract.map(AccountState::getCode).orElse(Bytes.EMPTY)))
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
            () -> Gas.of(quorumTx.getGasLimit()).minus(initialFrame.getRemainingGas()),
            () -> gasAvailable.minus(initialFrame.getRemainingGas()));
      }

      // Refund the sender by what we should and pay the miner fee (note that we're doing them one
      // after the other so that if it is the same account somehow, we end up with the right result)
      final Gas selfDestructRefund =
          gasCalculator.getSelfDestructRefundAmount().times(initialFrame.getSelfDestructs().size());
      final Gas refundGas = initialFrame.getGasRefund().plus(selfDestructRefund);
      final Gas refunded = refunded(quorumTx, initialFrame.getRemainingGas(), refundGas);
      final Wei refundedWei = refunded.priceFor(transactionGasPrice);
      senderMutableAccount.incrementBalance(refundedWei);

      final Gas gasUsedByTransaction =
          Gas.of(quorumTx.getGasLimit()).minus(initialFrame.getRemainingGas());

      final MutableAccount coinbase = publicWorldState.getOrCreate(miningBeneficiary).getMutable();
      final Gas coinbaseFee = Gas.of(quorumTx.getGasLimit()).minus(refunded);
      if (blockHeader.getBaseFee().isPresent()
          && transaction.getType().equals(TransactionType.EIP1559)) {
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
      final CoinbaseFeePriceCalculator coinbaseCreditService =
          transaction.getType().equals(TransactionType.FRONTIER)
              ? CoinbaseFeePriceCalculator.frontier()
              : coinbaseFeePriceCalculator;
      final Wei coinbaseWeiDelta =
          coinbaseCreditService.price(coinbaseFee, transactionGasPrice, blockHeader.getBaseFee());

      coinbase.incrementBalance(coinbaseWeiDelta);

      initialFrame.getSelfDestructs().forEach(privateWorldState::deleteAccount);

      if (clearEmptyAccounts) {
        clearEmptyAccounts(privateWorldState);
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
              TransactionInvalidReason.INTERNAL_ERROR,
              "Internal Error in Besu - " + re.toString()));
    }
  }
}
