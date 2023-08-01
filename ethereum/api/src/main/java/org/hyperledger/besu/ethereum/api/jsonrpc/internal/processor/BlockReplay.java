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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor;

import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessDataGasCalculator.calculateExcessDataGasForParent;

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer.TraceableState;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;

import java.util.List;
import java.util.Optional;

public class BlockReplay {

  private final ProtocolSchedule protocolSchedule;
  private final Blockchain blockchain;

  public BlockReplay(final ProtocolSchedule protocolSchedule, final Blockchain blockchain) {
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
  }

  public Optional<BlockTrace> block(
      final Block block, final TransactionAction<TransactionTrace> action) {
    return performActionWithBlock(
        block.getHeader(),
        block.getBody(),
        (body, header, blockchain, transactionProcessor, protocolSpec) -> {
          final Wei dataGasPrice =
              protocolSpec
                  .getFeeMarket()
                  .dataPricePerGas(
                      blockchain
                          .getBlockHeader(header.getParentHash())
                          .map(parent -> calculateExcessDataGasForParent(protocolSpec, parent))
                          .orElse(DataGas.ZERO));

          final List<TransactionTrace> transactionTraces =
              body.getTransactions().stream()
                  .map(
                      transaction ->
                          action.performAction(
                              transaction, header, blockchain, transactionProcessor, dataGasPrice))
                  .toList();
          return Optional.of(new BlockTrace(transactionTraces));
        });
  }

  public Optional<BlockTrace> block(
      final Hash blockHash, final TransactionAction<TransactionTrace> action) {
    return getBlock(blockHash).flatMap(block -> block(block, action));
  }

  public <T> Optional<T> beforeTransactionInBlock(
      final TraceableState mutableWorldState,
      final Hash blockHash,
      final Hash transactionHash,
      final TransactionAction<T> action) {
    return performActionWithBlock(
        blockHash,
        (body, header, blockchain, transactionProcessor, protocolSpec) -> {
          final BlockHashLookup blockHashLookup = new CachingBlockHashLookup(header, blockchain);
          final Wei dataGasPrice =
              protocolSpec
                  .getFeeMarket()
                  .dataPricePerGas(
                      blockchain
                          .getBlockHeader(header.getParentHash())
                          .map(parent -> calculateExcessDataGasForParent(protocolSpec, parent))
                          .orElse(DataGas.ZERO));

          for (final Transaction transaction : body.getTransactions()) {
            if (transaction.getHash().equals(transactionHash)) {
              return Optional.of(
                  action.performAction(
                      transaction, header, blockchain, transactionProcessor, dataGasPrice));
            } else {
              transactionProcessor.processTransaction(
                  blockchain,
                  mutableWorldState.updater(),
                  header,
                  transaction,
                  protocolSpec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
                  blockHashLookup,
                  false,
                  TransactionValidationParams.blockReplay(),
                  dataGasPrice);
            }
          }
          return Optional.empty();
        });
  }

  public <T> Optional<T> afterTransactionInBlock(
      final TraceableState mutableWorldState,
      final Hash blockHash,
      final Hash transactionHash,
      final TransactionAction<T> action) {
    return beforeTransactionInBlock(
        mutableWorldState,
        blockHash,
        transactionHash,
        (transaction, blockHeader, blockchain, transactionProcessor, dataGasPrice) -> {
          final ProtocolSpec spec = protocolSchedule.getByBlockHeader(blockHeader);
          transactionProcessor.processTransaction(
              blockchain,
              mutableWorldState.updater(),
              blockHeader,
              transaction,
              spec.getMiningBeneficiaryCalculator().calculateBeneficiary(blockHeader),
              new CachingBlockHashLookup(blockHeader, blockchain),
              false,
              TransactionValidationParams.blockReplay(),
              dataGasPrice);
          return action.performAction(
              transaction, blockHeader, blockchain, transactionProcessor, dataGasPrice);
        });
  }

  public <T> Optional<T> performActionWithBlock(final Hash blockHash, final BlockAction<T> action) {
    Optional<Block> maybeBlock = getBlock(blockHash);
    if (maybeBlock.isEmpty()) {
      maybeBlock = getBadBlock(blockHash);
    }
    return maybeBlock.flatMap(
        block -> performActionWithBlock(block.getHeader(), block.getBody(), action));
  }

  private <T> Optional<T> performActionWithBlock(
      final BlockHeader header, final BlockBody body, final BlockAction<T> action) {
    if (header == null) {
      return Optional.empty();
    }
    if (body == null) {
      return Optional.empty();
    }
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);
    final MainnetTransactionProcessor transactionProcessor = protocolSpec.getTransactionProcessor();

    return action.perform(body, header, blockchain, transactionProcessor, protocolSpec);
  }

  private Optional<Block> getBlock(final Hash blockHash) {
    final BlockHeader blockHeader = blockchain.getBlockHeader(blockHash).orElse(null);
    if (blockHeader != null) {
      final BlockBody blockBody = blockchain.getBlockBody(blockHeader.getHash()).orElse(null);
      if (blockBody != null) {
        return Optional.of(new Block(blockHeader, blockBody));
      }
    }
    return Optional.empty();
  }

  private Optional<Block> getBadBlock(final Hash blockHash) {
    final ProtocolSpec protocolSpec =
        protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader());
    return protocolSpec.getBadBlocksManager().getBadBlock(blockHash);
  }

  @FunctionalInterface
  public interface BlockAction<T> {
    Optional<T> perform(
        BlockBody body,
        BlockHeader blockHeader,
        Blockchain blockchain,
        MainnetTransactionProcessor transactionProcessor,
        ProtocolSpec protocolSpec);
  }

  @FunctionalInterface
  public interface TransactionAction<T> {
    T performAction(
        Transaction transaction,
        BlockHeader blockHeader,
        Blockchain blockchain,
        MainnetTransactionProcessor transactionProcessor,
        Wei dataGasPrice);
  }
}
