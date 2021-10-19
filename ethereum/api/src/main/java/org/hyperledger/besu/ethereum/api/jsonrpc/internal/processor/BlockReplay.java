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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BlockReplay {

  private final ProtocolSchedule protocolSchedule;
  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;

  public BlockReplay(
      final ProtocolSchedule protocolSchedule,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive) {
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
  }

  public Optional<BlockTrace> block(
      final Block block, final TransactionAction<TransactionTrace> action) {
    return performActionWithBlock(
        block.getHeader(),
        block.getBody(),
        (body, header, blockchain, mutableWorldState, transactionProcessor) -> {
          List<TransactionTrace> transactionTraces =
              body.getTransactions().stream()
                  .map(
                      transaction ->
                          action.performAction(
                              transaction,
                              header,
                              blockchain,
                              mutableWorldState,
                              transactionProcessor))
                  .collect(Collectors.toList());
          return Optional.of(new BlockTrace(transactionTraces));
        });
  }

  public Optional<BlockTrace> block(
      final Hash blockHash, final TransactionAction<TransactionTrace> action) {
    return getBlock(blockHash).flatMap(block -> block(block, action));
  }

  public <T> Optional<T> beforeTransactionInBlock(
      final Hash blockHash, final Hash transactionHash, final TransactionAction<T> action) {
    return performActionWithBlock(
        blockHash,
        (body, header, blockchain, mutableWorldState, transactionProcessor) -> {
          final BlockHashLookup blockHashLookup = new BlockHashLookup(header, blockchain);
          for (final Transaction transaction : body.getTransactions()) {
            if (transaction.getHash().equals(transactionHash)) {
              return Optional.of(
                  action.performAction(
                      transaction, header, blockchain, mutableWorldState, transactionProcessor));
            } else {
              final ProtocolSpec spec = protocolSchedule.getByBlockNumber(header.getNumber());
              transactionProcessor.processTransaction(
                  blockchain,
                  mutableWorldState.updater(),
                  header,
                  transaction,
                  spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
                  blockHashLookup,
                  false,
                  TransactionValidationParams.blockReplay());
            }
          }
          return Optional.empty();
        });
  }

  public <T> Optional<T> afterTransactionInBlock(
      final Hash blockHash, final Hash transactionHash, final TransactionAction<T> action) {
    return beforeTransactionInBlock(
        blockHash,
        transactionHash,
        (transaction, blockHeader, blockchain, worldState, transactionProcessor) -> {
          final ProtocolSpec spec = protocolSchedule.getByBlockNumber(blockHeader.getNumber());
          transactionProcessor.processTransaction(
              blockchain,
              worldState.updater(),
              blockHeader,
              transaction,
              spec.getMiningBeneficiaryCalculator().calculateBeneficiary(blockHeader),
              new BlockHashLookup(blockHeader, blockchain),
              false,
              TransactionValidationParams.blockReplay());
          return action.performAction(
              transaction, blockHeader, blockchain, worldState, transactionProcessor);
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
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());
    final MainnetTransactionProcessor transactionProcessor = protocolSpec.getTransactionProcessor();
    final BlockHeader previous = blockchain.getBlockHeader(header.getParentHash()).orElse(null);
    if (previous == null) {
      return Optional.empty();
    }
    final MutableWorldState mutableWorldState =
        worldStateArchive
            .getMutable(previous.getStateRoot(), previous.getHash(), false)
            .orElse(null);
    if (mutableWorldState == null) {
      return Optional.empty();
    }
    return action.perform(body, header, blockchain, mutableWorldState, transactionProcessor);
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
        protocolSchedule.getByBlockNumber(blockchain.getChainHeadHeader().getNumber());
    return protocolSpec.getBadBlocksManager().getBadBlock(blockHash);
  }

  @FunctionalInterface
  public interface BlockAction<T> {
    Optional<T> perform(
        BlockBody body,
        BlockHeader blockHeader,
        Blockchain blockchain,
        MutableWorldState worldState,
        MainnetTransactionProcessor transactionProcessor);
  }

  @FunctionalInterface
  public interface TransactionAction<T> {
    T performAction(
        Transaction transaction,
        BlockHeader blockHeader,
        Blockchain blockchain,
        MutableWorldState worldState,
        MainnetTransactionProcessor transactionProcessor);
  }
}
