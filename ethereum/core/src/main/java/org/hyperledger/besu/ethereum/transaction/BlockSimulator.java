/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverride;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.BlockOverrides;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Simulates the execution of a block, processing transactions and applying state overrides. This
 * class is responsible for simulating the execution of a block, which involves processing
 * transactions and applying state overrides. It provides a way to test and validate the behavior of
 * a block without actually executing it on the blockchain. The simulator takes into account various
 * factors, such as the block header, transaction calls, and state overrides, to simulate the
 * execution of the block. It returns a list of simulation results, which include the final block
 * header, transaction receipts, and other relevant information.
 */
public class BlockSimulator {
  private final TransactionSimulator transactionSimulator;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;
  private final MiningConfiguration miningConfiguration;

  public BlockSimulator(
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator,
      final MiningConfiguration miningConfiguration) {
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.transactionSimulator = transactionSimulator;
  }

  /**
   * Processes a list of BlockStateCalls sequentially, collecting the results.
   *
   * @param header The block header for all simulations.
   * @param blockStateCalls The list of BlockStateCalls to process.
   * @return A list of BlockSimulationResult objects from processing each BlockStateCall.
   */
  public List<BlockSimulationResult> process(
      final BlockHeader header, final List<? extends BlockStateCall> blockStateCalls) {
    try (final MutableWorldState ws =
        worldStateArchive
            .getMutable(header, false)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Public world state not available for block " + header.toLogString()))) {
      return process(header, blockStateCalls, ws);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException("Error simulating block", e);
    }
  }

  /**
   * Processes a list of BlockStateCalls sequentially, collecting the results.
   *
   * @param header The block header for all simulations.
   * @param blockStateCalls The list of BlockStateCalls to process.
   * @param worldState The initial MutableWorldState to start with.
   * @return A list of BlockSimulationResult objects from processing each BlockStateCall.
   */
  public List<BlockSimulationResult> process(
      final BlockHeader header,
      final List<? extends BlockStateCall> blockStateCalls,
      final MutableWorldState worldState) {
    List<BlockSimulationResult> simulationResults = new ArrayList<>();
    for (BlockStateCall blockStateCall : blockStateCalls) {
      BlockSimulationResult simulationResult =
          processSingleBlockStateCall(header, blockStateCall, worldState);
      simulationResults.add(simulationResult);
    }
    return simulationResults;
  }

  /**
   * Processes a single BlockStateCall, simulating the block execution.
   *
   * @param header The block header for the simulation.
   * @param blockStateCall The BlockStateCall to process.
   * @param ws The MutableWorldState to use for the simulation.
   * @return A BlockSimulationResult from processing the BlockStateCall.
   */
  private BlockSimulationResult processSingleBlockStateCall(
      final BlockHeader header, final BlockStateCall blockStateCall, final MutableWorldState ws) {
    BlockOverrides blockOverrides = blockStateCall.getBlockOverrides();
    long timestamp = blockOverrides.getTimestamp().orElse(header.getTimestamp() + 1);
    ProtocolSpec newProtocolSpec = protocolSchedule.getForNextBlockHeader(header, timestamp);

    // Apply block header overrides and state overrides
    BlockHeader blockHeader = applyBlockHeaderOverrides(header, newProtocolSpec, blockOverrides);
    blockStateCall.getStateOverrideMap().ifPresent(overrides -> applyStateOverrides(overrides, ws));

    // Override the mining beneficiary calculator if a fee recipient is specified, otherwise use the
    // default
    MiningBeneficiaryCalculator miningBeneficiaryCalculator =
        getMiningBeneficiaryCalculator(blockOverrides, newProtocolSpec);

    List<TransactionSimulatorResult> transactionSimulatorResults =
        processTransactions(blockHeader, blockStateCall, ws, miningBeneficiaryCalculator);

    return finalizeBlock(
        blockHeader, blockStateCall, ws, newProtocolSpec, transactionSimulatorResults);
  }

  @VisibleForTesting
  protected List<TransactionSimulatorResult> processTransactions(
      final BlockHeader blockHeader,
      final BlockStateCall blockStateCall,
      final MutableWorldState ws,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {

    List<TransactionSimulatorResult> transactionSimulations = new ArrayList<>();

    for (CallParameter callParameter : blockStateCall.getCalls()) {
      final WorldUpdater transactionUpdater = ws.updater();

      final Optional<TransactionSimulatorResult> transactionSimulatorResult =
          transactionSimulator.processWithWorldUpdater(
              callParameter,
              Optional.empty(), // We have already applied state overrides on block level
              buildTransactionValidationParams(blockStateCall.isValidate()),
              OperationTracer.NO_TRACING,
              blockHeader,
              transactionUpdater,
              miningBeneficiaryCalculator);

      if (transactionSimulatorResult.isEmpty()) {
        throw new BlockSimulationException("Transaction simulator result is empty");
      }

      TransactionSimulatorResult result = transactionSimulatorResult.get();
      if (result.isInvalid()) {
        throw new BlockSimulationException(
            "Transaction simulator result is invalid: " + result.getInvalidReason().orElse(null));
      }
      transactionSimulations.add(transactionSimulatorResult.get());
      transactionUpdater.commit();
    }
    return transactionSimulations;
  }

  @VisibleForTesting
  protected BlockSimulationResult finalizeBlock(
      final BlockHeader blockHeader,
      final BlockStateCall blockStateCall,
      final MutableWorldState ws,
      final ProtocolSpec protocolSpec,
      final List<TransactionSimulatorResult> transactionSimulations) {

    long currentGasUsed = 0;
    final var transactionReceiptFactory = protocolSpec.getTransactionReceiptFactory();

    final List<TransactionReceipt> receipts = new ArrayList<>();
    final List<Transaction> transactions = new ArrayList<>();

    for (TransactionSimulatorResult transactionSimulatorResult : transactionSimulations) {

      TransactionProcessingResult transactionProcessingResult = transactionSimulatorResult.result();
      final Transaction transaction = transactionSimulatorResult.transaction();

      currentGasUsed += transaction.getGasLimit() - transactionProcessingResult.getGasRemaining();

      final TransactionReceipt transactionReceipt =
          transactionReceiptFactory.create(
              transaction.getType(), transactionProcessingResult, ws, currentGasUsed);

      receipts.add(transactionReceipt);
      transactions.add(transaction);
    }

    BlockHeader finalBlockHeader =
        createFinalBlockHeader(
            blockHeader,
            ws,
            transactions,
            blockStateCall.getBlockOverrides(),
            receipts,
            currentGasUsed);
    Block block = new Block(finalBlockHeader, new BlockBody(transactions, List.of()));
    return new BlockSimulationResult(block, receipts, transactionSimulations);
  }

  /**
   * Applies state overrides to the world state.
   *
   * @param stateOverrideMap The StateOverrideMap containing the state overrides.
   * @param ws The MutableWorldState to apply the overrides to.
   */
  @VisibleForTesting
  protected void applyStateOverrides(
      final StateOverrideMap stateOverrideMap, final MutableWorldState ws) {
    var updater = ws.updater();
    for (Address accountToOverride : stateOverrideMap.keySet()) {
      final StateOverride override = stateOverrideMap.get(accountToOverride);
      MutableAccount account = updater.getOrCreate(accountToOverride);
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
    updater.commit();
  }

  /**
   * Applies block header overrides to the block header.
   *
   * @param header The original block header.
   * @param newProtocolSpec The ProtocolSpec for the block.
   * @param blockOverrides The BlockOverrides to apply.
   * @return The modified block header.
   */
  @VisibleForTesting
  protected BlockHeader applyBlockHeaderOverrides(
      final BlockHeader header,
      final ProtocolSpec newProtocolSpec,
      final BlockOverrides blockOverrides) {
    long timestamp = blockOverrides.getTimestamp().orElse(header.getTimestamp() + 1);
    long blockNumber = blockOverrides.getBlockNumber().orElse(header.getNumber() + 1);

    return BlockHeaderBuilder.createDefault()
        .parentHash(header.getHash())
        .timestamp(timestamp)
        .number(blockNumber)
        .coinbase(
            blockOverrides
                .getFeeRecipient()
                .orElseGet(() -> miningConfiguration.getCoinbase().orElseThrow()))
        .difficulty(
            blockOverrides.getDifficulty().isPresent()
                ? Difficulty.of(blockOverrides.getDifficulty().get())
                : header.getDifficulty())
        .gasLimit(
            blockOverrides
                .getGasLimit()
                .orElseGet(() -> getNextGasLimit(newProtocolSpec, header, blockNumber)))
        .baseFee(
            blockOverrides
                .getBaseFeePerGas()
                .orElseGet(() -> getNextBaseFee(newProtocolSpec, header, blockNumber)))
        .mixHash(blockOverrides.getMixHashOrPrevRandao().orElse(Hash.EMPTY))
        .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
        .blockHeaderFunctions(new SimulatorBlockHeaderFunctions(blockOverrides))
        .buildBlockHeader();
  }

  /**
   * Creates the final block header after applying state changes and transaction processing.
   *
   * @param blockHeader The original block header.
   * @param ws The MutableWorldState after applying state overrides.
   * @param transactions The list of transactions in the block.
   * @param blockOverrides The BlockOverrides to apply.
   * @param receipts The list of transaction receipts.
   * @param currentGasUsed The total gas used in the block.
   * @return The final block header.
   */
  private BlockHeader createFinalBlockHeader(
      final BlockHeader blockHeader,
      final MutableWorldState ws,
      final List<Transaction> transactions,
      final BlockOverrides blockOverrides,
      final List<TransactionReceipt> receipts,
      final long currentGasUsed) {

    return BlockHeaderBuilder.createDefault()
        .populateFrom(blockHeader)
        .ommersHash(BodyValidation.ommersHash(List.of()))
        .stateRoot(blockOverrides.getStateRoot().orElse(ws.rootHash()))
        .transactionsRoot(BodyValidation.transactionsRoot(transactions))
        .receiptsRoot(BodyValidation.receiptsRoot(receipts))
        .logsBloom(BodyValidation.logsBloom(receipts))
        .gasUsed(currentGasUsed)
        .withdrawalsRoot(null)
        .requestsHash(null)
        .mixHash(blockOverrides.getMixHashOrPrevRandao().orElse(Hash.EMPTY))
        .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
        .blockHeaderFunctions(new SimulatorBlockHeaderFunctions(blockOverrides))
        .buildBlockHeader();
  }

  /**
   * Builds the TransactionValidationParams for the block simulation.
   *
   * @param shouldValidate Whether to validate transactions.
   * @return The TransactionValidationParams for the block simulation.
   */
  @VisibleForTesting
  ImmutableTransactionValidationParams buildTransactionValidationParams(
      final boolean shouldValidate) {

    if (shouldValidate) {
      return ImmutableTransactionValidationParams.builder()
          .from(TransactionValidationParams.processingBlock())
          .build();
    }

    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.transactionSimulator())
        .isAllowExceedingBalance(true)
        .build();
  }

  private long getNextGasLimit(
      final ProtocolSpec protocolSpec, final BlockHeader parentHeader, final long blockNumber) {
    return protocolSpec
        .getGasLimitCalculator()
        .nextGasLimit(
            parentHeader.getGasLimit(),
            miningConfiguration.getTargetGasLimit().orElse(parentHeader.getGasLimit()),
            blockNumber);
  }

  /**
   * Override the mining beneficiary calculator if a fee recipient is specified, otherwise use the
   * default
   */
  private MiningBeneficiaryCalculator getMiningBeneficiaryCalculator(
      final BlockOverrides blockOverrides, final ProtocolSpec newProtocolSpec) {
    if (blockOverrides.getFeeRecipient().isPresent()) {
      return blockHeader -> blockOverrides.getFeeRecipient().get();
    } else {
      return newProtocolSpec.getMiningBeneficiaryCalculator();
    }
  }

  private Wei getNextBaseFee(
      final ProtocolSpec protocolSpec, final BlockHeader parentHeader, final long blockNumber) {
    return Optional.of(protocolSpec.getFeeMarket())
        .filter(FeeMarket::implementsBaseFee)
        .map(BaseFeeMarket.class::cast)
        .map(
            feeMarket ->
                feeMarket.computeBaseFee(
                    blockNumber,
                    parentHeader.getBaseFee().orElse(Wei.ZERO),
                    parentHeader.getGasUsed(),
                    feeMarket.targetGasUsed(parentHeader)))
        .orElse(null);
  }

  private static class SimulatorBlockHeaderFunctions implements BlockHeaderFunctions {

    private final BlockOverrides blockOverrides;
    private final MainnetBlockHeaderFunctions blockHeaderFunctions =
        new MainnetBlockHeaderFunctions();

    private SimulatorBlockHeaderFunctions(final BlockOverrides blockOverrides) {
      this.blockOverrides = blockOverrides;
    }

    @Override
    public Hash hash(final BlockHeader header) {
      return blockOverrides.getBlockHash().orElseGet(() -> blockHeaderFunctions.hash(header));
    }

    @Override
    public ParsedExtraData parseExtraData(final BlockHeader header) {
      return blockHeaderFunctions.parseExtraData(header);
    }
  }
}
