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

import org.hyperledger.besu.datatypes.AccountOverride;
import org.hyperledger.besu.datatypes.AccountOverrideMap;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlockOverrides;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * BlockSimulator is responsible for simulating the execution of a block. It processes transactions
 * and applies state overrides to simulate the block execution.
 */
public class BlockSimulator {
  private final TransactionSimulator transactionSimulator;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;
  private final MiningConfiguration miningConfiguration;

  public BlockSimulator(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final long rpcGasCap,
      final MiningConfiguration miningConfiguration) {
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.miningConfiguration = miningConfiguration;
    this.transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule, rpcGasCap);
  }

  public List<BlockSimulationResult> process(
      final BlockHeader header, final List<? extends BlockStateCall> blockStateCalls) {
    try (final MutableWorldState ws = getWorldState(header)) {
      return processWithMutableWorldState(header, blockStateCalls, ws);
    } catch (final Exception e) {
      throw new RuntimeException("Error simulating block", e);
    }
  }

  public BlockSimulationResult process(
      final BlockHeader header, final BlockStateCall blockStateCall) {
    try (final MutableWorldState ws = getWorldState(header)) {
      return processWithMutableWorldState(header, blockStateCall, ws);
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
  private List<BlockSimulationResult> processWithMutableWorldState(
      final BlockHeader header,
      final List<? extends BlockStateCall> blockStateCalls,
      final MutableWorldState worldState) {
    List<BlockSimulationResult> simulationResults = new ArrayList<>();
    for (BlockStateCall blockStateCall : blockStateCalls) {
      BlockSimulationResult simulationResult =
          processWithMutableWorldState(header, blockStateCall, worldState);
      simulationResults.add(simulationResult);
    }
    return simulationResults;
  }

  protected BlockSimulationResult processWithMutableWorldState(
      final BlockHeader header, final BlockStateCall blockStateCall, final MutableWorldState ws) {
    WorldUpdater updater = ws.updater();
    BlockOverrides blockOverrides = blockStateCall.getBlockOverrides();
    long timestamp = blockOverrides.getTimestamp().orElse(header.getTimestamp() + 1);
    ProtocolSpec newProtocolSpec = protocolSchedule.getForNextBlockHeader(header, timestamp);

    BlockHeader blockHeader = applyBlockHeaderOverrides(header, newProtocolSpec, blockOverrides);
    blockStateCall
        .getAccountOverrides()
        .ifPresent(overrides -> applyStateOverrides(overrides, updater));

    MiningBeneficiaryCalculator miningBeneficiaryCalculator =
        getMiningBeneficiaryCalculator(blockOverrides, newProtocolSpec);

    List<TransactionSimulatorResult> transactionSimulatorResults =
        processTransactions(blockHeader, blockStateCall, updater, miningBeneficiaryCalculator);
    updater.commit();

    return processPostExecution(
        blockHeader, ws, transactionSimulatorResults, blockOverrides, newProtocolSpec);
  }

  private List<TransactionSimulatorResult> processTransactions(
      final BlockHeader blockHeader,
      final BlockStateCall blockStateCall,
      final WorldUpdater updater,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator) {
    List<TransactionSimulatorResult> transactionSimulations = new ArrayList<>();
    for (CallParameter callParameter : blockStateCall.getCalls()) {
      transactionSimulations.add(
          processTransaction(
              callParameter,
              blockHeader,
              updater,
              miningBeneficiaryCalculator,
              blockStateCall.isValidate()));
    }
    return transactionSimulations;
  }

  private TransactionSimulatorResult processTransaction(
      final CallParameter callParameter,
      final BlockHeader blockHeader,
      final WorldUpdater updater,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean shouldValidate) {
    final WorldUpdater localUpdater = updater.updater();
    final Optional<TransactionSimulatorResult> transactionSimulatorResult =
        transactionSimulator.processWithWorldUpdater(
            callParameter,
            Optional.empty(),
            buildTransactionValidationParams(shouldValidate),
            OperationTracer.NO_TRACING,
            blockHeader,
            localUpdater,
            miningBeneficiaryCalculator);

    if (transactionSimulatorResult.isEmpty()) {
      throw new RuntimeException("Transaction simulator result is empty");
    }

    TransactionSimulatorResult result = transactionSimulatorResult.get();
    if (result.result().isSuccessful()) {
      localUpdater.commit();
    }
    return result;
  }

  private BlockSimulationResult processPostExecution(
      final BlockHeader blockHeader,
      final MutableWorldState ws,
      final List<TransactionSimulatorResult> transactionSimulations,
      final BlockOverrides blockOverrides,
      final ProtocolSpec newProtocolSpec) {

    long currentGasUsed = 0;
    final var transactionReceiptFactory = newProtocolSpec.getTransactionReceiptFactory();
    final List<TransactionReceipt> receipts = new ArrayList<>();
    final List<Transaction> transactions = new ArrayList<>();

    for (TransactionSimulatorResult transactionSimulatorResult : transactionSimulations) {
      TransactionProcessingResult transactionProcessingResult = transactionSimulatorResult.result();
      if (transactionProcessingResult.isSuccessful()) {
        processSuccessfulTransaction(
            transactionSimulatorResult,
            ws,
            transactionReceiptFactory,
            receipts,
            transactions,
            currentGasUsed);
        currentGasUsed +=
            transactionSimulatorResult.transaction().getGasLimit()
                - transactionProcessingResult.getGasRemaining();
      }
    }

    BlockHeader finalBlockHeader =
        createFinalBlockHeader(
            blockHeader, ws, transactions, blockOverrides, receipts, currentGasUsed);
    Block block = new Block(finalBlockHeader, new BlockBody(transactions, List.of()));
    return new BlockSimulationResult(block, receipts, transactionSimulations);
  }

  private void processSuccessfulTransaction(
      final TransactionSimulatorResult transactionSimulatorResult,
      final MutableWorldState ws,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final List<TransactionReceipt> receipts,
      final List<Transaction> transactions,
      final long currentGasUsed) {
    TransactionProcessingResult transactionProcessingResult = transactionSimulatorResult.result();
    final Transaction transaction = transactionSimulatorResult.transaction();
    final TransactionReceipt transactionReceipt =
        transactionReceiptFactory.create(
            transaction.getType(),
            transactionProcessingResult,
            ws,
            currentGasUsed
                + transaction.getGasLimit()
                - transactionProcessingResult.getGasRemaining());
    receipts.add(transactionReceipt);
    transactions.add(transaction);
  }

  private void applyStateOverrides(
      final AccountOverrideMap accountOverrideMap, final WorldUpdater updater) {
    for (Address accountToOverride : accountOverrideMap.keySet()) {
      final AccountOverride override = accountOverrideMap.get(accountToOverride);
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
  }

  private BlockHeader applyBlockHeaderOverrides(
      final BlockHeader header,
      final ProtocolSpec newProtocolSpec,
      final BlockOverrides blockOverrides) {
    long timestamp = blockOverrides.getTimestamp().orElse(header.getTimestamp() + 1);
    long blockNumber = blockOverrides.getBlockNumber().orElse(header.getNumber() + 1);

    return BlockHeaderBuilder.createDefault()
        .parentHash(header.getHash())
        .coinbase(
            blockOverrides
                .getFeeRecipient()
                .orElse(miningConfiguration.getCoinbase().orElseThrow()))
        .difficulty(
            Difficulty.of(
                blockOverrides
                    .getDifficulty()
                    .orElse(getNextDifficulty(newProtocolSpec, header, timestamp))))
        .number(blockNumber)
        .gasLimit(
            blockOverrides
                .getGasLimit()
                .orElse(getNextGasLimit(newProtocolSpec, header, blockNumber)))
        .timestamp(timestamp)
        .baseFee(
            blockOverrides
                .getBaseFeePerGas()
                .orElse(getNextBaseFee(newProtocolSpec, header, blockNumber)))
        .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
        .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
        .buildBlockHeader();
  }

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
        .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
        .extraData(blockOverrides.getExtraData().orElse(Bytes.EMPTY))
        .buildBlockHeader();
  }

  private MutableWorldState getWorldState(final BlockHeader header) {
    return worldStateArchive
        .getMutable(header, false)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Public world state not available for block " + header.toLogString()));
  }

  private ImmutableTransactionValidationParams buildTransactionValidationParams(
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

  private BigInteger getNextDifficulty(
      final ProtocolSpec protocolSpec, final BlockHeader parentHeader, final long timestamp) {
    final DifficultyCalculator difficultyCalculator = protocolSpec.getDifficultyCalculator();
    return difficultyCalculator.nextDifficulty(timestamp, parentHeader);
  }

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
    final Wei baseFee =
        Optional.of(protocolSpec.getFeeMarket())
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
    return baseFee;
  }
}
