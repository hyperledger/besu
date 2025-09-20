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

import org.hyperledger.besu.crypto.SECPSignature;
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
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessListFactory;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.TransactionAccessList;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessingContext;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessorCoordinator;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.ethereum.transaction.exceptions.BlockStateCallException;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.tracing.EthTransferLogOperationTracer;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.BlockOverrides;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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
      TransactionValidationParams.blockSimulatorStrict();

  private static final TransactionValidationParams SIMULATION_PARAMS =
      TransactionValidationParams.transactionSimulatorAllowExceedingBalanceAndFutureNonce();

  private final TransactionSimulator transactionSimulator;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;
  private final MiningConfiguration miningConfiguration;
  private final Blockchain blockchain;
  private final long rpcGasCap;

  public BlockSimulator(
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final TransactionSimulator transactionSimulator,
      final MiningConfiguration miningConfiguration,
      final Blockchain blockchain,
      final long rpcGasCap) {
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.transactionSimulator = transactionSimulator;
    this.blockchain = blockchain;
    this.rpcGasCap = rpcGasCap;
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
    int countStateCalls = simulationParameter.getBlockStateCalls().size();
    List<BlockSimulationResult> results = new ArrayList<>(countStateCalls);

    // Fill gaps between blocks and set the correct block number and timestamp
    List<BlockStateCall> blockStateCalls =
        fillBlockStateCalls(simulationParameter.getBlockStateCalls(), blockHeader);

    BlockHeader currentBlockHeader = blockHeader;
    HashMap<Long, Hash> blockHashCache = HashMap.newHashMap(countStateCalls);
    long simulationCumulativeGasUsed = 0;
    for (BlockStateCall stateCall : blockStateCalls) {
      BlockSimulationResult result =
          processBlockStateCall(
              currentBlockHeader,
              stateCall,
              worldState,
              simulationParameter.isValidation(),
              simulationParameter.isTraceTransfers(),
              simulationParameter::getFakeSignature,
              blockHashCache,
              simulationCumulativeGasUsed);
      results.add(result);
      BlockHeader resultBlockHeader = result.getBlock().getHeader();
      blockHashCache.put(resultBlockHeader.getNumber(), resultBlockHeader.getHash());
      currentBlockHeader = resultBlockHeader;
      simulationCumulativeGasUsed += resultBlockHeader.getGasUsed();
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
      final boolean shouldValidate,
      final boolean isTraceTransfers,
      final Supplier<SECPSignature> signatureSupplier,
      final Map<Long, Hash> blockHashCache,
      final long simulationCumulativeGasUsed) {

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
        createBlockHashLookup(
            blockOverrides, protocolSpec, overridenBaseBlockHeader, blockHashCache);

    final BlockProcessingContext blockProcessingContext =
        new BlockProcessingContext(
            overridenBaseBlockHeader,
            ws,
            protocolSpec,
            blockHashLookup,
            OperationTracer.NO_TRACING);
    protocolSpec.getPreExecutionProcessor().process(blockProcessingContext);

    BlockStateCallSimulationResult blockStateCallSimulationResult =
        processTransactions(
            overridenBaseBlockHeader,
            blockStateCall,
            ws,
            protocolSpec,
            shouldValidate,
            isTraceTransfers,
            transactionProcessor,
            blockHashLookup,
            signatureSupplier,
            simulationCumulativeGasUsed);

    // EIP-7685: process EL requests
    final Optional<RequestProcessorCoordinator> requestProcessor =
        protocolSpec.getRequestProcessorCoordinator();
    Optional<List<Request>> maybeRequests = Optional.empty();
    if (requestProcessor.isPresent()) {
      RequestProcessingContext requestProcessingContext =
          new RequestProcessingContext(
              blockProcessingContext, blockStateCallSimulationResult.getReceipts());
      maybeRequests = Optional.of(requestProcessor.get().process(requestProcessingContext));
    }

    return createFinalBlock(
        overridenBaseBlockHeader,
        blockStateCallSimulationResult,
        blockOverrides,
        ws,
        maybeRequests);
  }

  protected BlockStateCallSimulationResult processTransactions(
      final BlockHeader blockHeader,
      final BlockStateCall blockStateCall,
      final MutableWorldState ws,
      final ProtocolSpec protocolSpec,
      final boolean shouldValidate,
      final boolean isTraceTransfers,
      final MainnetTransactionProcessor transactionProcessor,
      final BlockHashLookup blockHashLookup,
      final Supplier<SECPSignature> signatureSupplier,
      final long simulationCumulativeGasUsed) {

    TransactionValidationParams transactionValidationParams =
        shouldValidate ? STRICT_VALIDATION_PARAMS : SIMULATION_PARAMS;

    BlockStateCallSimulationResult blockStateCallSimulationResult =
        new BlockStateCallSimulationResult(
            protocolSpec, calculateSimulationGasCap(blockHeader, simulationCumulativeGasUsed));

    MiningBeneficiaryCalculator miningBeneficiaryCalculator =
        blockStateCall
            .getBlockOverrides()
            .getFeeRecipient()
            .<MiningBeneficiaryCalculator>map(feeRecipient -> header -> feeRecipient)
            .orElseGet(protocolSpec::getMiningBeneficiaryCalculator);

    final BlockAccessList.BlockAccessListBuilder balBuilder = BlockAccessList.builder();
    final boolean includeBlockAccessList =
        protocolSpec
            .getBlockAccessListFactory()
            .map(BlockAccessListFactory::isForkActivated)
            .orElse(false);

    final WorldUpdater blockUpdater = ws.updater();
    for (int transactionLocation = 0;
        transactionLocation < blockStateCall.getCalls().size();
        transactionLocation++) {
      final WorldUpdater transactionUpdater = blockUpdater.updater();
      final CallParameter callParameter = blockStateCall.getCalls().get(transactionLocation);
      OperationTracer operationTracer =
          isTraceTransfers ? new EthTransferLogOperationTracer() : OperationTracer.NO_TRACING;

      long gasLimit =
          transactionSimulator.calculateSimulationGasCap(
              blockHeader,
              callParameter.getGas(),
              blockStateCallSimulationResult.getRemainingGas());

      BiFunction<ProtocolSpec, Optional<BlockHeader>, Wei> blobGasPricePerGasSupplier =
          getBlobGasPricePerGasSupplier(
              blockStateCall.getBlockOverrides(), transactionValidationParams);

      final TransactionAccessList transactionAccessList =
          new TransactionAccessList(transactionLocation);
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
              blockHashLookup,
              signatureSupplier,
              Optional.of(transactionAccessList));

      TransactionSimulatorResult transactionSimulationResult =
          transactionSimulatorResult.orElseThrow(
              () -> new BlockStateCallException("Transaction simulator result is empty"));

      if (includeBlockAccessList
          && transactionUpdater instanceof StackedUpdater<?, ?> stackedUpdater) {
        transactionSimulationResult
            .result()
            .getTransactionAccessList()
            .ifPresent(t -> balBuilder.addTransactionLevelAccessList(t, stackedUpdater));
      }

      if (transactionSimulationResult.isInvalid()) {
        throw new BlockStateCallException(
            "Transaction simulator result is invalid", transactionSimulationResult);
      }

      transactionUpdater.commit();
      blockUpdater.commit();

      blockStateCallSimulationResult.add(transactionSimulationResult, ws, operationTracer);
    }

    if (includeBlockAccessList) {
      blockStateCallSimulationResult.set(balBuilder.build());
    }
    return blockStateCallSimulationResult;
  }

  private BlockSimulationResult createFinalBlock(
      final BlockHeader blockHeader,
      final BlockStateCallSimulationResult blockStateCallSimulationResult,
      final BlockOverrides blockOverrides,
      final MutableWorldState ws,
      final Optional<List<Request>> maybeRequests) {

    List<Transaction> transactions = blockStateCallSimulationResult.getTransactions();
    List<TransactionReceipt> receipts = blockStateCallSimulationResult.getReceipts();

    BlockHeader finalBlockHeader =
        BlockHeaderBuilder.createDefault()
            .populateFrom(blockHeader)
            .ommersHash(BodyValidation.ommersHash(List.of()))
            .stateRoot(blockOverrides.getStateRoot().orElseGet(ws::frontierRootHash))
            .transactionsRoot(BodyValidation.transactionsRoot(transactions))
            .receiptsRoot(BodyValidation.receiptsRoot(receipts))
            .logsBloom(BodyValidation.logsBloom(receipts))
            .gasUsed(blockStateCallSimulationResult.getCumulativeGasUsed())
            .withdrawalsRoot(BodyValidation.withdrawalsRoot(List.of()))
            .requestsHash(maybeRequests.map(BodyValidation::requestsHash).orElse(null))
            .balHash(
                blockStateCallSimulationResult
                    .getBlockAccessList()
                    .map(BodyValidation::balHash)
                    .orElse(null))
            .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
            .blockHeaderFunctions(new BlockStateCallBlockHeaderFunctions(blockOverrides))
            .buildBlockHeader();

    Block block =
        new Block(
            finalBlockHeader,
            new BlockBody(
                transactions,
                List.of(),
                Optional.of(List.of()),
                blockStateCallSimulationResult.getBlockAccessList()));

    return new BlockSimulationResult(block, blockStateCallSimulationResult);
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

    // For PoS, coinbase is always configured, but for PoA it is not configured,
    // rather generated for each block via MiningBeneficiaryCalculator.
    // For simulation, if not configured, we use a dummy address 0x00.
    // We don't throw an exception if coinbase is not configured.
    BlockHeaderBuilder builder =
        BlockHeaderBuilder.createDefault()
            .parentHash(header.getHash())
            .timestamp(timestamp)
            .number(blockNumber)
            .coinbase(
                blockOverrides
                    .getFeeRecipient()
                    .orElseGet(() -> miningConfiguration.getCoinbase().orElse(Address.ZERO)))
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
   * @param blockHashCache A cache of block hashes.
   * @return The BlockHashLookup for the block simulation.
   */
  private BlockHashLookup createBlockHashLookup(
      final BlockOverrides blockOverrides,
      final ProtocolSpec newProtocolSpec,
      final BlockHeader blockHeader,
      final Map<Long, Hash> blockHashCache) {
    var blockCallBlockHashLookup =
        blockOverrides
            .getBlockHashLookup()
            .<BlockHashLookup>map(
                blockHashLookup -> (___, blockNumber) -> blockHashLookup.apply(blockNumber))
            .orElseGet(
                () ->
                    newProtocolSpec
                        .getPreExecutionProcessor()
                        .createBlockHashLookup(blockchain, blockHeader));
    return (frame, blockNumber) -> {
      if (blockHashCache.containsKey(blockNumber)) {
        return blockHashCache.get(blockNumber);
      }
      return blockCallBlockHashLookup.apply(frame, blockNumber);
    };
  }

  public long calculateSimulationGasCap(
      final BlockHeader blockHeader, final long simulationCumulativeGasUsed) {
    if (rpcGasCap > 0) {
      long remainingGas = Math.max(rpcGasCap - simulationCumulativeGasUsed, 0);
      return Math.min(remainingGas, blockHeader.getGasLimit());
    }
    return blockHeader.getGasLimit();
  }
}
