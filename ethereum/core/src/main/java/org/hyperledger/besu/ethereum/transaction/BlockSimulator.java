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
import org.hyperledger.besu.datatypes.BlockOverrides;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
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
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

public class BlockSimulator {
  private final TransactionSimulator transactionSimulator;
  private final WorldStateArchive worldStateArchive;
  private final ProtocolSchedule protocolSchedule;

  private final Supplier<Address> coinbaseSupplier;
  private final Supplier<OptionalLong> nextGasSupplier;

  private final MutableBlockchain blockchain;

  public BlockSimulator(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final long rpcGasCap,
      final Supplier<Address> coinbaseSupplier,
      final Supplier<OptionalLong> nextGasSupplier) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.protocolSchedule = protocolSchedule;
    this.coinbaseSupplier = coinbaseSupplier;
    this.nextGasSupplier = nextGasSupplier;
    this.transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule, rpcGasCap);
  }

  public BlockSimulationResult simulate(
      final BlockHeader header, final BlockStateCall blockStateCall) {
    return simulate(header, blockStateCall, false);
  }

  public BlockSimulationResult simulate(
      final BlockHeader header, final BlockStateCall blockStateCall, final boolean shouldPersist) {

    long currentGasUsed = 0;
    final List<TransactionReceipt> receipts = new ArrayList<>();
    final List<Transaction> transactions = new ArrayList<>();

    final BlockOverrides blockOverrides = blockStateCall.getBlockOverrides();

    long timestamp = blockOverrides.getTimestamp().orElse(header.getTimestamp() + 1);
    final ProtocolSpec newProtocolSpec = protocolSchedule.getForNextBlockHeader(header, timestamp);

    final var transactionReceiptFactory = newProtocolSpec.getTransactionReceiptFactory();

    final BlockHeader blockHeader = overrideBlockHeader(header, blockOverrides, newProtocolSpec);

    final MiningBeneficiaryCalculator miningBeneficiaryCalculator =
        getMiningBeneficiaryCalculator(blockOverrides, newProtocolSpec);

    try (final MutableWorldState ws = getWorldState(header, shouldPersist)) {
      WorldUpdater updater = ws.updater();

      for (CallParameter callParameter : blockStateCall.getCalls()) {
        final WorldUpdater localUpdater = updater.updater();
        final Optional<TransactionSimulatorResult> transactionSimulatorResult =
            transactionSimulator.processWithWorldUpdater(
                callParameter,
                Optional.empty(),
                buildTransactionValidationParams(),
                OperationTracer.NO_TRACING,
                blockHeader,
                localUpdater,
                miningBeneficiaryCalculator);

        if (transactionSimulatorResult.isEmpty()) {
          return new BlockSimulationResult(
              new BlockProcessingResult(Optional.empty(), "Transaction processing failed"));
        }

        TransactionProcessingResult transactionProcessingResult =
            transactionSimulatorResult.get().result();

        if (transactionProcessingResult.isSuccessful()) {
          localUpdater.commit();
          currentGasUsed +=
              callParameter.getGasLimit() - transactionProcessingResult.getGasRemaining();

          Transaction transaction = transactionSimulatorResult.get().transaction();
          transactions.add(transaction);
          final TransactionReceipt transactionReceipt =
              transactionReceiptFactory.create(
                  transaction.getType(), transactionProcessingResult, ws, currentGasUsed);
          receipts.add(transactionReceipt);
        } else {
          return new BlockSimulationResult(
              new BlockProcessingResult(
                  Optional.empty(),
                  transactionProcessingResult
                      .getInvalidReason()
                      .orElse("Transaction processing failed")));
        }
      }

      updater.commit();
      BlockHeader finalBlockHeader =
          createFinalBlockHeader(
              blockHeader, ws, transactions, blockOverrides, receipts, currentGasUsed);

      if (shouldPersist) {
        ws.persist(finalBlockHeader);
        blockchain.storeBlock(
            new Block(finalBlockHeader, new BlockBody(transactions, List.of())), receipts);
      }
      Block block = new Block(finalBlockHeader, new BlockBody(transactions, List.of()));
      BlockProcessingOutputs outputs = new BlockProcessingOutputs(ws, receipts);
      BlockProcessingResult result = new BlockProcessingResult(Optional.of(outputs));
      return new BlockSimulationResult(block.getHeader(), block.getBody(), receipts, result);

    } catch (final Exception e) {
      throw new RuntimeException("Error simulating block", e);
    }
  }

  private BlockHeader overrideBlockHeader(
      final BlockHeader header,
      final BlockOverrides blockOverrides,
      final ProtocolSpec newProtocolSpec) {
    long timestamp = blockOverrides.getTimestamp().orElse(header.getTimestamp() + 1);
    long blockNumber = blockOverrides.getBlockNumber().orElse(header.getNumber() + 1);

    return BlockHeaderBuilder.createDefault()
        .parentHash(header.getHash())
        .coinbase(blockOverrides.getFeeRecipient().orElse(coinbaseSupplier.get()))
        .difficulty(
            Difficulty.of(getNextDifficulty(blockOverrides, newProtocolSpec, header, timestamp)))
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

  private MutableWorldState getWorldState(final BlockHeader header, final boolean shouldPersist) {
    return worldStateArchive
        .getMutable(header, shouldPersist)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Public world state not available for block " + header.toLogString()));
  }

  private ImmutableTransactionValidationParams buildTransactionValidationParams() {
    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.processingBlock())
        .build();
  }

  private long getNextGasLimit(
      final ProtocolSpec protocolSpec, final BlockHeader parentHeader, final long blockNumber) {
    return protocolSpec
        .getGasLimitCalculator()
        .nextGasLimit(
            parentHeader.getGasLimit(),
            nextGasSupplier.get().orElse(parentHeader.getGasLimit()),
            blockNumber);
  }

  private BigInteger getNextDifficulty(
      final BlockOverrides blockOverrides,
      final ProtocolSpec protocolSpec,
      final BlockHeader parentHeader,
      final long timestamp) {

    if (blockOverrides.getDifficulty().isPresent()) {
      return blockOverrides.getDifficulty().get();
    } else {
      final DifficultyCalculator difficultyCalculator = protocolSpec.getDifficultyCalculator();
      return difficultyCalculator.nextDifficulty(timestamp, parentHeader);
    }
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
