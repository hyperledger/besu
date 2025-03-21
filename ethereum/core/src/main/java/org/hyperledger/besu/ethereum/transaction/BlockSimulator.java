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

import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;
import static org.hyperledger.besu.ethereum.transaction.BlockStateCalls.fillBlockStateCalls;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverride;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
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
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.exceptions.BlockStateCallException;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.BlockOverrides;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

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

  private static final TransactionValidationParams STRICT_VALIDATION_PARAMS =
      TransactionValidationParams.transactionSimulator();

  private static final TransactionValidationParams SIMULATION_PARAMS =
      TransactionValidationParams.transactionSimulatorAllowExceedingBalance();

  private final TransactionSimulator transactionSimulator;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;
  private final MiningConfiguration miningConfiguration;
  private final Blockchain blockchain;

  public BlockSimulator(
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator,
      final MiningConfiguration miningConfiguration,
      final Blockchain blockchain) {
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.transactionSimulator = transactionSimulator;
    this.blockchain = blockchain;
  }

  /**
   * Processes a list of BlockStateCalls sequentially, collecting the results.
   *
   * @param header The block header for all simulations.
   * @param blockSimulationParameter The BlockSimulationParameter containing the block state calls.
   * @return A list of BlockSimulationResult objects from processing each BlockStateCall.
   */
  public List<BlockSimulationResult> process(
      final BlockHeader header, final BlockSimulationParameter blockSimulationParameter) {
    try (final MutableWorldState ws = getWorldState(header)) {
      return process(header, blockSimulationParameter, ws);
    } catch (IllegalArgumentException | BlockStateCallException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException("Error simulating block", e);
    }
  }

  /**
   * Processes a list of BlockStateCalls sequentially, collecting the results.
   *
   * @param blockHeader The block header for all simulations.
   * @param simulationParameter The BlockSimulationParameter containing the block state calls.
   * @param worldState The initial MutableWorldState to start with.
   * @return A list of BlockSimulationResult objects from processing each BlockStateCall.
   */
  public List<BlockSimulationResult> process(
      final BlockHeader blockHeader,
      final BlockSimulationParameter simulationParameter,
      final MutableWorldState worldState) {
    List<BlockSimulationResult> results = new ArrayList<>();

    // Fill gaps between blocks and set the correct block number and timestamp
    List<BlockStateCall> blockStateCalls =
        fillBlockStateCalls(simulationParameter.getBlockStateCalls(), blockHeader);

    BlockHeader currentBlockHeader = blockHeader;
    for (BlockStateCall stateCall : blockStateCalls) {
      BlockSimulationResult result =
          processBlockStateCall(
              currentBlockHeader, stateCall, worldState, simulationParameter.isValidation());
      results.add(result);
      currentBlockHeader = result.getBlock().getHeader();
    }
    return results;
  }

  /**
   * Processes a single BlockStateCall, simulating the block execution.
   *
   * @param baseBlockHeader The block header for the simulation.
   * @param blockStateCall The BlockStateCall to process.
   * @param ws The MutableWorldState to use for the simulation.
   * @return A BlockSimulationResult from processing the BlockStateCall.
   */
  private BlockSimulationResult processBlockStateCall(
      final BlockHeader baseBlockHeader,
      final BlockStateCall blockStateCall,
      final MutableWorldState ws,
      final boolean shouldValidate) {

    BlockOverrides blockOverrides = blockStateCall.getBlockOverrides();
    ProtocolSpec protocolSpec =
        protocolSchedule.getForNextBlockHeader(
            baseBlockHeader, blockOverrides.getTimestamp().orElseThrow());

    BlockHeader overridenBaseBlockHeader =
        overrideBlockHeader(baseBlockHeader, protocolSpec, blockOverrides, shouldValidate);

    blockStateCall
        .getStateOverrideMap()
        .ifPresent(stateOverrideMap -> applyStateOverrides(stateOverrideMap, ws));

    // Create the transaction processor with precompile address overrides
    MainnetTransactionProcessor transactionProcessor =
        new SimulationTransactionProcessorFactory(protocolSchedule)
            .getTransactionProcessor(
                overridenBaseBlockHeader, blockStateCall.getStateOverrideMap());

    BlockHashLookup blockHashLookup =
        createBlockHashLookup(blockOverrides, protocolSpec, overridenBaseBlockHeader);

    BlockCallSimulationResult blockCallSimulationResult =
        processTransactions(
            overridenBaseBlockHeader,
            blockStateCall,
            ws,
            protocolSpec,
            shouldValidate,
            transactionProcessor,
            blockHashLookup);

    return createFinalBlock(
        overridenBaseBlockHeader, blockCallSimulationResult, blockOverrides, ws);
  }

  protected BlockCallSimulationResult processTransactions(
      final BlockHeader blockHeader,
      final BlockStateCall blockStateCall,
      final MutableWorldState ws,
      final ProtocolSpec protocolSpec,
      final boolean shouldValidate,
      final MainnetTransactionProcessor transactionProcessor,
      final BlockHashLookup blockHashLookup) {

    TransactionValidationParams transactionValidationParams =
        shouldValidate ? STRICT_VALIDATION_PARAMS : SIMULATION_PARAMS;

    BlockCallSimulationResult blockCallSimulationResult =
        new BlockCallSimulationResult(protocolSpec, blockHeader.getGasLimit());

    MiningBeneficiaryCalculator miningBeneficiaryCalculator =
        blockStateCall
            .getBlockOverrides()
            .getFeeRecipient()
            .<MiningBeneficiaryCalculator>map(feeRecipient -> header -> feeRecipient)
            .orElseGet(protocolSpec::getMiningBeneficiaryCalculator);

    for (CallParameter callParameter : blockStateCall.getCalls()) {

      OperationTracer operationTracer = OperationTracer.NO_TRACING;

      final WorldUpdater transactionUpdater = ws.updater();
      long gasLimit =
          transactionSimulator.calculateSimulationGasCap(
              callParameter.getGasLimit(), blockCallSimulationResult.getRemainingGas());

      BiFunction<ProtocolSpec, Optional<BlockHeader>, Wei> blobGasPricePerGasSupplier =
          getBlobGasPricePerGasSupplier(
              blockStateCall.getBlockOverrides(), transactionValidationParams);

      final Optional<TransactionSimulatorResult> transactionSimulatorResult =
          transactionSimulator.processWithWorldUpdater(
              callParameter,
              Optional.empty(), // We have already applied state overrides on block level
              transactionValidationParams,
              operationTracer,
              blockHeader,
              transactionUpdater,
              miningBeneficiaryCalculator.calculateBeneficiary(blockHeader),
              gasLimit,
              transactionProcessor,
              blobGasPricePerGasSupplier,
              blockHashLookup);

      TransactionSimulatorResult transactionSimulationResult =
          transactionSimulatorResult.orElseThrow(
              () -> new BlockStateCallException("Transaction simulator result is empty"));

      if (transactionSimulationResult.isInvalid()) {
        throw new BlockStateCallException(
            "Transaction simulator result is invalid", transactionSimulationResult);
      }
      transactionUpdater.commit();
      blockCallSimulationResult.add(transactionSimulationResult, ws);
    }
    return blockCallSimulationResult;
  }

  private BlockSimulationResult createFinalBlock(
      final BlockHeader blockHeader,
      final BlockCallSimulationResult blockCallSimulationResult,
      final BlockOverrides blockOverrides,
      final MutableWorldState ws) {

    List<Transaction> transactions = blockCallSimulationResult.getTransactions();
    List<TransactionReceipt> receipts = blockCallSimulationResult.getReceipts();

    BlockHeader finalBlockHeader =
        BlockHeaderBuilder.createDefault()
            .populateFrom(blockHeader)
            .ommersHash(BodyValidation.ommersHash(List.of()))
            .stateRoot(blockOverrides.getStateRoot().orElseGet(ws::rootHash))
            .transactionsRoot(BodyValidation.transactionsRoot(transactions))
            .receiptsRoot(BodyValidation.receiptsRoot(receipts))
            .logsBloom(BodyValidation.logsBloom(receipts))
            .gasUsed(blockCallSimulationResult.getCumulativeGasUsed())
            .withdrawalsRoot(BodyValidation.withdrawalsRoot(List.of()))
            .requestsHash(null)
            .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
            .blockHeaderFunctions(new BlockStateCallBlockHeaderFunctions(blockOverrides))
            .buildBlockHeader();

    Block block =
        new Block(finalBlockHeader, new BlockBody(transactions, List.of(), Optional.of(List.of())));

    return new BlockSimulationResult(block, blockCallSimulationResult);
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
      TransactionSimulator.applyOverrides(account, override);
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
  protected BlockHeader overrideBlockHeader(
      final BlockHeader header,
      final ProtocolSpec newProtocolSpec,
      final BlockOverrides blockOverrides,
      final boolean shouldValidate) {
    long timestamp = blockOverrides.getTimestamp().orElseThrow();
    long blockNumber = blockOverrides.getBlockNumber().orElseThrow();

    BlockHeaderBuilder builder =
        BlockHeaderBuilder.createDefault()
            .parentHash(header.getHash())
            .timestamp(timestamp)
            .number(blockNumber)
            .coinbase(
                blockOverrides
                    .getFeeRecipient()
                    .orElseGet(() -> miningConfiguration.getCoinbase().orElseThrow()))
            .difficulty(
                blockOverrides.getDifficulty().map(Difficulty::of).orElseGet(header::getDifficulty))
            .gasLimit(
                blockOverrides
                    .getGasLimit()
                    .orElseGet(() -> getNextGasLimit(newProtocolSpec, header, blockNumber)))
            .baseFee(
                blockOverrides
                    .getBaseFeePerGas()
                    .orElseGet(
                        () ->
                            shouldValidate
                                ? getNextBaseFee(newProtocolSpec, header, blockNumber)
                                : Wei.ZERO))
            .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
            .parentBeaconBlockRoot(Bytes32.ZERO)
            .prevRandao(blockOverrides.getMixHashOrPrevRandao().orElse(Bytes32.ZERO));

    return builder
        .blockHeaderFunctions(new BlockStateCallBlockHeaderFunctions(blockOverrides))
        .buildBlockHeader();
  }

  private BiFunction<ProtocolSpec, Optional<BlockHeader>, Wei> getBlobGasPricePerGasSupplier(
      final BlockOverrides blockOverrides,
      final TransactionValidationParams transactionValidationParams) {
    if (blockOverrides.getBlobBaseFee().isPresent()) {
      return (protocolSchedule, blockHeader) -> blockOverrides.getBlobBaseFee().get();
    }
    return (protocolSpec, maybeParentHeader) -> {
      if (transactionValidationParams.isAllowExceedingBalance()) {
        return Wei.ZERO;
      }
      return protocolSpec
          .getFeeMarket()
          .blobGasPricePerGas(
              maybeParentHeader
                  .map(parent -> calculateExcessBlobGasForParent(protocolSpec, parent))
                  .orElse(BlobGas.ZERO));
    };
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

  private MutableWorldState getWorldState(final BlockHeader blockHeader) {
    return worldStateArchive
        .getWorldState(withBlockHeaderAndNoUpdateNodeHead(blockHeader))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Public world state not available for block " + blockHeader.toLogString()));
  }

  private static class BlockStateCallBlockHeaderFunctions implements BlockHeaderFunctions {

    private final BlockOverrides blockOverrides;
    private final MainnetBlockHeaderFunctions blockHeaderFunctions =
        new MainnetBlockHeaderFunctions();

    private BlockStateCallBlockHeaderFunctions(final BlockOverrides blockOverrides) {
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

  /**
   * Creates a BlockHashLookup for the block simulation. If a BlockHashLookup is provided in the
   * BlockOverrides, it is used. Otherwise, the default BlockHashLookup is created.
   *
   * @param blockOverrides The BlockOverrides to use.
   * @param newProtocolSpec The ProtocolSpec for the block.
   * @param blockHeader The block header for the simulation.
   * @return The BlockHashLookup for the block simulation.
   */
  private BlockHashLookup createBlockHashLookup(
      final BlockOverrides blockOverrides,
      final ProtocolSpec newProtocolSpec,
      final BlockHeader blockHeader) {
    return blockOverrides
        .getBlockHashLookup()
        .<BlockHashLookup>map(
            blockHashLookup -> (frame, blockNumber) -> blockHashLookup.apply(blockNumber))
        .orElseGet(
            () ->
                newProtocolSpec
                    .getBlockHashProcessor()
                    .createBlockHashLookup(blockchain, blockHeader));
  }
}
