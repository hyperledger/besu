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
package org.hyperledger.besu.ethereum.transaction;

import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessDataGasCalculator.calculateExcessDataGasForParent;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/*
 * Used to process transactions for eth_call and eth_estimateGas.
 *
 * The processing won't affect the world state, it is used to execute read operations on the
 * blockchain or to estimate the transaction gas cost.
 */
public class TransactionSimulator {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  // Dummy signature for transactions to not fail being processed.
  private static final SECPSignature FAKE_SIGNATURE =
      SIGNATURE_ALGORITHM
          .get()
          .createSignature(
              SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
              SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
              (byte) 0);

  // TODO: Identify a better default from account to use, such as the registered
  // coinbase or an account currently unlocked by the client.
  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;

  public TransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final long blockNumber) {
    final BlockHeader header = blockchain.getBlockHeader(blockNumber).orElse(null);
    return process(
        callParams,
        transactionValidationParams,
        operationTracer,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        header);
  }

  public Optional<TransactionSimulatorResult> processAtHead(final CallParameter callParams) {
    return process(
        callParams,
        ImmutableTransactionValidationParams.builder()
            .from(TransactionValidationParams.transactionSimulator())
            .isAllowExceedingBalance(true)
            .build(),
        OperationTracer.NO_TRACING,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        blockchain.getChainHeadHeader());
  }

  /**
   * Processes a transaction simulation with the provided parameters and executes pre-worldstate
   * close actions.
   *
   * @param callParams The call parameters for the transaction.
   * @param transactionValidationParams The validation parameters for the transaction.
   * @param operationTracer The tracer for capturing operations during processing.
   * @param preWorldStateCloseGuard The pre-worldstate close guard for executing pre-close actions.
   * @param header The block header.
   * @return An Optional containing the result of the processing.
   */
  public <U> Optional<U> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final PreCloseStateHandler<U> preWorldStateCloseGuard,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }

    try (final MutableWorldState ws = getWorldState(header)) {

      WorldUpdater updater = getEffectiveWorldStateUpdater(header, ws);

      // in order to trace the state diff we need to make sure that
      // the world updater always has a parent
      if (operationTracer instanceof DebugOperationTracer) {
        updater = updater.parentUpdater().isPresent() ? updater : updater.updater();
      }

      return preWorldStateCloseGuard.apply(
          ws,
          processWithWorldUpdater(
              callParams, transactionValidationParams, operationTracer, header, updater));

    } catch (final Exception e) {
      return Optional.empty();
    }
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams, final Hash blockHeaderHash) {
    final BlockHeader header = blockchain.getBlockHeader(blockHeaderHash).orElse(null);
    return process(
        callParams,
        TransactionValidationParams.transactionSimulator(),
        OperationTracer.NO_TRACING,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        header);
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams, final long blockNumber) {
    return process(
        callParams,
        TransactionValidationParams.transactionSimulator(),
        OperationTracer.NO_TRACING,
        blockNumber);
  }

  private MutableWorldState getWorldState(final BlockHeader header) {
    return worldStateArchive
        .getMutable(header, false)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Public world state not available for block " + header.toLogString()));
  }

  @Nonnull
  public Optional<TransactionSimulatorResult> processWithWorldUpdater(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final BlockHeader header,
      final WorldUpdater updater) {
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);

    final Address senderAddress =
        callParams.getFrom() != null ? callParams.getFrom() : DEFAULT_FROM;

    BlockHeader blockHeaderToProcess = header;

    if (transactionValidationParams.isAllowExceedingBalance() && header.getBaseFee().isPresent()) {
      blockHeaderToProcess =
          BlockHeaderBuilder.fromHeader(header)
              .baseFee(Wei.ZERO)
              .blockHeaderFunctions(protocolSpec.getBlockHeaderFunctions())
              .buildBlockHeader();
    }

    final Account sender = updater.get(senderAddress);
    final long nonce = sender != null ? sender.getNonce() : 0L;

    final long gasLimit =
        callParams.getGasLimit() >= 0
            ? callParams.getGasLimit()
            : blockHeaderToProcess.getGasLimit();
    final Wei value = callParams.getValue() != null ? callParams.getValue() : Wei.ZERO;
    final Bytes payload = callParams.getPayload() != null ? callParams.getPayload() : Bytes.EMPTY;

    final MainnetTransactionProcessor transactionProcessor =
        protocolSchedule.getByBlockHeader(blockHeaderToProcess).getTransactionProcessor();

    final Optional<Transaction> maybeTransaction =
        buildTransaction(
            callParams,
            transactionValidationParams,
            header,
            senderAddress,
            nonce,
            gasLimit,
            value,
            payload);
    if (maybeTransaction.isEmpty()) {
      return Optional.empty();
    }

    final Optional<BlockHeader> maybeParentHeader =
        blockchain.getBlockHeader(blockHeaderToProcess.getParentHash());
    final Wei dataGasPrice =
        protocolSpec
            .getFeeMarket()
            .dataPricePerGas(
                maybeParentHeader
                    .map(parent -> calculateExcessDataGasForParent(protocolSpec, parent))
                    .orElse(DataGas.ZERO));

    final Transaction transaction = maybeTransaction.get();
    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            blockchain,
            updater,
            blockHeaderToProcess,
            transaction,
            protocolSpec
                .getMiningBeneficiaryCalculator()
                .calculateBeneficiary(blockHeaderToProcess),
            new CachingBlockHashLookup(blockHeaderToProcess, blockchain),
            false,
            transactionValidationParams,
            operationTracer,
            dataGasPrice);

    return Optional.of(new TransactionSimulatorResult(transaction, result));
  }

  private Optional<Transaction> buildTransaction(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final BlockHeader header,
      final Address senderAddress,
      final long nonce,
      final long gasLimit,
      final Wei value,
      final Bytes payload) {
    final Transaction.Builder transactionBuilder =
        Transaction.builder()
            .nonce(nonce)
            .gasLimit(gasLimit)
            .to(callParams.getTo())
            .sender(senderAddress)
            .value(value)
            .payload(payload)
            .signature(FAKE_SIGNATURE);

    // Set access list if present
    callParams.getAccessList().ifPresent(transactionBuilder::accessList);

    final Wei gasPrice;
    final Wei maxFeePerGas;
    final Wei maxPriorityFeePerGas;
    if (transactionValidationParams.isAllowExceedingBalance()) {
      gasPrice = Wei.ZERO;
      maxFeePerGas = Wei.ZERO;
      maxPriorityFeePerGas = Wei.ZERO;
    } else {
      gasPrice = callParams.getGasPrice() != null ? callParams.getGasPrice() : Wei.ZERO;
      maxFeePerGas = callParams.getMaxFeePerGas().orElse(gasPrice);
      maxPriorityFeePerGas = callParams.getMaxPriorityFeePerGas().orElse(gasPrice);
    }
    if (header.getBaseFee().isEmpty()) {
      transactionBuilder.gasPrice(gasPrice);
    } else if (protocolSchedule.getChainId().isPresent()) {
      transactionBuilder.maxFeePerGas(maxFeePerGas).maxPriorityFeePerGas(maxPriorityFeePerGas);
    } else {
      return Optional.empty();
    }

    transactionBuilder.guessType();
    if (transactionBuilder.getTransactionType().requiresChainId()) {
      transactionBuilder.chainId(
          protocolSchedule
              .getChainId()
              .orElse(BigInteger.ONE)); // needed to make some transactions valid
    }

    final Transaction transaction = transactionBuilder.build();
    return Optional.ofNullable(transaction);
  }

  public WorldUpdater getEffectiveWorldStateUpdater(
      final BlockHeader header, final MutableWorldState publicWorldState) {
    return publicWorldState.updater();
  }

  public Optional<Boolean> doesAddressExistAtHead(final Address address) {
    final BlockHeader header = blockchain.getChainHeadHeader();
    try (final MutableWorldState worldState =
        worldStateArchive.getMutable(header, false).orElseThrow()) {
      return doesAddressExist(worldState, address, header);
    } catch (final Exception ex) {
      return Optional.empty();
    }
  }

  public Optional<Boolean> doesAddressExist(
      final MutableWorldState worldState, final Address address, final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }
    if (worldState == null) {
      return Optional.empty();
    }

    return Optional.of(worldState.get(address) != null);
  }
}
