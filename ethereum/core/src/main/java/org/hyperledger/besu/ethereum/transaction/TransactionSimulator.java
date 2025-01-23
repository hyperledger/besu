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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverride;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Used to process transactions for eth_call and eth_estimateGas.
 *
 * The processing won't affect the world state, it is used to execute read operations on the
 * blockchain or to estimate the transaction gas cost.
 */
public class TransactionSimulator {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionSimulator.class);
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
  private final MiningConfiguration miningConfiguration;
  private final SimulationTransactionProcessorFactory simulationTransactionProcessorFactory;
  private final long rpcGasCap;

  public TransactionSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final MiningConfiguration miningConfiguration,
      final long rpcGasCap) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.rpcGasCap = rpcGasCap;
    this.simulationTransactionProcessorFactory =
        new SimulationTransactionProcessorFactory(protocolSchedule);
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final long blockNumber) {
    final BlockHeader header = blockchain.getBlockHeader(blockNumber).orElse(null);
    return process(
        callParams,
        Optional.empty(),
        transactionValidationParams,
        operationTracer,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        header);
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final BlockHeader blockHeader) {
    return process(
        callParams,
        Optional.empty(),
        transactionValidationParams,
        operationTracer,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        blockHeader);
  }

  public Optional<TransactionSimulatorResult> process(
      final CallParameter callParams,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final BlockHeader blockHeader) {
    return process(
        callParams,
        maybeStateOverrides,
        transactionValidationParams,
        operationTracer,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        blockHeader);
  }

  public Optional<TransactionSimulatorResult> processOnPending(
      final CallParameter callParams,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final ProcessableBlockHeader pendingBlockHeader) {

    try (final MutableWorldState disposableWorldState =
        duplicateWorldStateAtParent(pendingBlockHeader.getParentHash())) {
      WorldUpdater updater = getEffectiveWorldStateUpdater(disposableWorldState);

      // in order to trace the state diff we need to make sure that
      // the world updater always has a parent
      if (operationTracer instanceof DebugOperationTracer) {
        updater = updater.parentUpdater().isPresent() ? updater : updater.updater();
      }

      return processWithWorldUpdater(
          callParams,
          maybeStateOverrides,
          transactionValidationParams,
          operationTracer,
          pendingBlockHeader,
          updater,
          Address.ZERO);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ProcessableBlockHeader simulatePendingBlockHeader() {
    final long timestamp = MILLISECONDS.toSeconds(System.currentTimeMillis());
    final var chainHeadHeader = blockchain.getChainHeadHeader();
    final ProtocolSpec protocolSpec =
        protocolSchedule.getForNextBlockHeader(chainHeadHeader, timestamp);

    final var simulatedBlockHeader =
        BlockHeaderBuilder.createPending(
                protocolSpec,
                chainHeadHeader,
                miningConfiguration,
                timestamp,
                Optional.empty(),
                Optional.empty())
            .buildProcessableBlockHeader();

    LOG.trace("Simulated block header: {}", simulatedBlockHeader);

    return simulatedBlockHeader;
  }

  private MutableWorldState duplicateWorldStateAtParent(final Hash parentHash) {
    final var parentHeader =
        blockchain
            .getBlockHeader(parentHash)
            .orElseThrow(
                () ->
                    new IllegalStateException("Block with hash " + parentHash + " not available"));

    final Hash parentStateRoot = parentHeader.getStateRoot();
    return worldStateArchive
        .getMutable(parentHeader, false)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "World state not available for block "
                        + parentHeader.getNumber()
                        + " with state root "
                        + parentStateRoot));
  }

  public Optional<TransactionSimulatorResult> processAtHead(final CallParameter callParams) {
    final var chainHeadHash = blockchain.getChainHeadHash();
    return process(
        callParams,
        TransactionValidationParams.transactionSimulatorAllowExceedingBalance(),
        OperationTracer.NO_TRACING,
        (mutableWorldState, transactionSimulatorResult) -> transactionSimulatorResult,
        blockchain
            .getBlockHeader(chainHeadHash)
            .or(() -> blockchain.getBlockHeaderSafe(chainHeadHash))
            .orElse(null));
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
    return process(
        callParams,
        Optional.empty(),
        transactionValidationParams,
        operationTracer,
        preWorldStateCloseGuard,
        header);
  }

  /**
   * Processes a transaction simulation with the provided parameters and executes pre-worldstate
   * close actions.
   *
   * @param callParams The call parameters for the transaction.
   * @param maybeStateOverrides The map of state overrides to apply to the state for this
   *     transaction.
   * @param transactionValidationParams The validation parameters for the transaction.
   * @param operationTracer The tracer for capturing operations during processing.
   * @param preWorldStateCloseGuard The pre-worldstate close guard for executing pre-close actions.
   * @param header The block header.
   * @return An Optional containing the result of the processing.
   */
  public <U> Optional<U> process(
      final CallParameter callParams,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final PreCloseStateHandler<U> preWorldStateCloseGuard,
      final BlockHeader header) {
    if (header == null) {
      return Optional.empty();
    }

    try (final MutableWorldState ws = getWorldState(header)) {

      WorldUpdater updater = getEffectiveWorldStateUpdater(ws);

      // in order to trace the state diff we need to make sure that
      // the world updater always has a parent
      if (operationTracer instanceof DebugOperationTracer) {
        updater = updater.parentUpdater().isPresent() ? updater : updater.updater();
      }

      final var miningBeneficiary =
          protocolSchedule
              .getByBlockHeader(header)
              .getMiningBeneficiaryCalculator()
              .calculateBeneficiary(header);

      return preWorldStateCloseGuard.apply(
          ws,
          processWithWorldUpdater(
              callParams,
              maybeStateOverrides,
              transactionValidationParams,
              operationTracer,
              header,
              updater,
              miningBeneficiary));

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
      final Optional<StateOverrideMap> maybeStateOverrides,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final BlockHeader header,
      final WorldUpdater updater,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {

    final Address miningBeneficiary = miningBeneficiaryCalculator.calculateBeneficiary(header);

    return processWithWorldUpdater(
        callParams,
        maybeStateOverrides,
        transactionValidationParams,
        operationTracer,
        header,
        updater,
        miningBeneficiary);
  }

  @Nonnull
  public Optional<TransactionSimulatorResult> processWithWorldUpdater(
      final CallParameter callParams,
      final Optional<StateOverrideMap> maybeStateOverrides,
      final TransactionValidationParams transactionValidationParams,
      final OperationTracer operationTracer,
      final ProcessableBlockHeader processableHeader,
      final WorldUpdater updater,
      final Address miningBeneficiary) {
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(processableHeader);

    final Address senderAddress =
        callParams.getFrom() != null ? callParams.getFrom() : DEFAULT_FROM;

    final ProcessableBlockHeader blockHeaderToProcess;
    if (transactionValidationParams.isAllowExceedingBalance()
        && processableHeader.getBaseFee().isPresent()) {
      blockHeaderToProcess =
          new BlockHeaderBuilder()
              .populateFrom(processableHeader)
              .baseFee(Wei.ZERO)
              .blockHeaderFunctions(protocolSpec.getBlockHeaderFunctions())
              .buildProcessableBlockHeader();
    } else {
      blockHeaderToProcess = processableHeader;
    }
    if (maybeStateOverrides.isPresent()) {
      for (Address accountToOverride : maybeStateOverrides.get().keySet()) {
        final StateOverride overrides = maybeStateOverrides.get().get(accountToOverride);
        applyOverrides(updater.getOrCreate(accountToOverride), overrides);
      }
    }

    final long nonce =
        callParams
            .getNonce()
            .orElseGet(
                () ->
                    Optional.ofNullable(updater.get(senderAddress))
                        .map(Account::getNonce)
                        .orElse(0L));

    final long simulationGasCap =
        calculateSimulationGasCap(callParams.getGasLimit(), blockHeaderToProcess.getGasLimit());

    MainnetTransactionProcessor transactionProcessor =
        simulationTransactionProcessorFactory.getTransactionProcessor(
            processableHeader, maybeStateOverrides);

    final Optional<BlockHeader> maybeParentHeader =
        blockchain.getBlockHeader(blockHeaderToProcess.getParentHash());
    final Wei blobGasPrice =
        transactionValidationParams.isAllowExceedingBalance()
            ? Wei.ZERO
            : protocolSpec
                .getFeeMarket()
                .blobGasPricePerGas(
                    maybeParentHeader
                        .map(parent -> calculateExcessBlobGasForParent(protocolSpec, parent))
                        .orElse(BlobGas.ZERO));

    final Optional<Transaction> maybeTransaction =
        buildTransaction(
            callParams,
            transactionValidationParams,
            processableHeader,
            senderAddress,
            nonce,
            simulationGasCap,
            blobGasPrice);
    if (maybeTransaction.isEmpty()) {
      return Optional.empty();
    }

    final Transaction transaction = maybeTransaction.get();
    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            updater,
            blockHeaderToProcess,
            transaction,
            miningBeneficiary,
            protocolSpec
                .getBlockHashProcessor()
                .createBlockHashLookup(blockchain, blockHeaderToProcess),
            false,
            transactionValidationParams,
            operationTracer,
            blobGasPrice);

    return Optional.of(new TransactionSimulatorResult(transaction, result));
  }

  @VisibleForTesting
  protected void applyOverrides(final MutableAccount account, final StateOverride override) {
    LOG.debug("applying overrides to state for account {}", account.getAddress());
    override.getNonce().ifPresent(account::setNonce);
    if (override.getBalance().isPresent()) {
      account.setBalance(override.getBalance().get());
    }
    override.getCode().ifPresent(n -> account.setCode(Bytes.fromHexString(n)));
    override
        .getStateDiff()
        .ifPresent(
            d ->
                d.forEach(
                    (key, value) ->
                        account.setStorageValue(
                            UInt256.fromHexString(key), UInt256.fromHexString(value))));
  }

  private long calculateSimulationGasCap(
      final long userProvidedGasLimit, final long blockGasLimit) {
    final long simulationGasCap;

    // when not set gas limit is -1
    if (userProvidedGasLimit >= 0) {
      if (rpcGasCap > 0 && userProvidedGasLimit > rpcGasCap) {
        LOG.trace(
            "User provided gas limit {} is bigger than the value of rpc-gas-cap {}, setting simulation gas cap to the latter",
            userProvidedGasLimit,
            rpcGasCap);
        simulationGasCap = rpcGasCap;
      } else {
        LOG.trace("Using provided gas limit {} set as simulation gas cap", userProvidedGasLimit);
        simulationGasCap = userProvidedGasLimit;
      }
    } else {
      if (rpcGasCap > 0) {
        LOG.trace(
            "No user provided gas limit, setting simulation gas cap to the value of rpc-gas-cap {}",
            rpcGasCap);
        simulationGasCap = rpcGasCap;
      } else {
        simulationGasCap = blockGasLimit;
        LOG.trace(
            "No user provided gas limit and rpc-gas-cap options is not set, setting simulation gas cap to block gas limit {}",
            blockGasLimit);
      }
    }
    return simulationGasCap;
  }

  private Optional<Transaction> buildTransaction(
      final CallParameter callParams,
      final TransactionValidationParams transactionValidationParams,
      final ProcessableBlockHeader processableHeader,
      final Address senderAddress,
      final long nonce,
      final long gasLimit,
      final Wei blobGasPrice) {

    final Wei value = callParams.getValue() != null ? callParams.getValue() : Wei.ZERO;
    final Bytes payload = callParams.getPayload() != null ? callParams.getPayload() : Bytes.EMPTY;

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
    // Set versioned hashes if present
    callParams.getBlobVersionedHashes().ifPresent(transactionBuilder::versionedHashes);

    final Wei gasPrice;
    final Wei maxFeePerGas;
    final Wei maxPriorityFeePerGas;
    final Wei maxFeePerBlobGas;
    if (transactionValidationParams.isAllowExceedingBalance()) {
      gasPrice = Wei.ZERO;
      maxFeePerGas = Wei.ZERO;
      maxPriorityFeePerGas = Wei.ZERO;
      maxFeePerBlobGas = Wei.ZERO;
    } else {
      gasPrice = callParams.getGasPrice() != null ? callParams.getGasPrice() : Wei.ZERO;
      maxFeePerGas = callParams.getMaxFeePerGas().orElse(gasPrice);
      maxPriorityFeePerGas = callParams.getMaxPriorityFeePerGas().orElse(gasPrice);
      maxFeePerBlobGas = callParams.getMaxFeePerBlobGas().orElse(blobGasPrice);
    }

    if (shouldSetGasPrice(callParams, processableHeader)) {
      transactionBuilder.gasPrice(gasPrice);
    }

    if (shouldSetMaxFeePerGas(callParams, processableHeader)) {
      transactionBuilder.maxFeePerGas(maxFeePerGas).maxPriorityFeePerGas(maxPriorityFeePerGas);
    }

    if (shouldSetBlobGasPrice(callParams)) {
      transactionBuilder.maxFeePerBlobGas(maxFeePerBlobGas);
    }

    transactionBuilder.guessType();

    if (transactionBuilder.getTransactionType().requiresChainId()) {
      callParams
          .getChainId()
          .ifPresentOrElse(
              transactionBuilder::chainId,
              () ->
                  // needed to make some transactions valid
                  transactionBuilder.chainId(protocolSchedule.getChainId().orElse(BigInteger.ONE)));
    }

    final Transaction transaction = transactionBuilder.build();
    return Optional.ofNullable(transaction);
  }

  public WorldUpdater getEffectiveWorldStateUpdater(final MutableWorldState publicWorldState) {
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

  private boolean shouldSetGasPrice(
      final CallParameter callParams, final ProcessableBlockHeader header) {
    if (header.getBaseFee().isEmpty()) {
      return true;
    }

    // if maxPriorityFeePerGas and maxFeePerGas are not set, use gasPrice
    return callParams.getMaxPriorityFeePerGas().isEmpty() && callParams.getMaxFeePerGas().isEmpty();
  }

  private boolean shouldSetMaxFeePerGas(
      final CallParameter callParams, final ProcessableBlockHeader header) {
    if (protocolSchedule.getChainId().isEmpty()) {
      return false;
    }

    if (header.getBaseFee().isEmpty()) {
      return false;
    }

    if (shouldSetBlobGasPrice(callParams)) {
      return true;
    }

    // only set maxFeePerGas and maxPriorityFeePerGas if they are present, otherwise transaction
    // will be considered EIP-1559 transaction even if the simulation is for a legacy transaction
    return callParams.getMaxPriorityFeePerGas().isPresent()
        || callParams.getMaxFeePerGas().isPresent();
  }

  private boolean shouldSetBlobGasPrice(final CallParameter callParams) {
    if (protocolSchedule.getChainId().isEmpty()) {
      return false;
    }
    return callParams.getBlobVersionedHashes().isPresent();
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }
}
