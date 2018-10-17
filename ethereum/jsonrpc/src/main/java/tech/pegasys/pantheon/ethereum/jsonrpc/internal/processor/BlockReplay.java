/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;

import java.util.Optional;

public class BlockReplay {

  private final ProtocolSchedule<?> protocolSchedule;
  private final Blockchain blockchain;
  private final WorldStateArchive worldStateArchive;

  public BlockReplay(
      final ProtocolSchedule<?> protocolSchedule,
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive) {
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
  }

  public <T> Optional<T> beforeTransactionInBlock(
      final Hash blockHash, final Hash transactionHash, final Action<T> action) {
    final BlockHeader header = blockchain.getBlockHeader(blockHash).orElse(null);
    if (header == null) {
      return Optional.empty();
    }
    final BlockBody body = blockchain.getBlockBody(header.getHash()).orElse(null);
    if (body == null) {
      return Optional.empty();
    }
    final ProtocolSpec<?> protocolSpec = protocolSchedule.getByBlockNumber(header.getNumber());
    final TransactionProcessor transactionProcessor = protocolSpec.getTransactionProcessor();
    final BlockHeader previous = blockchain.getBlockHeader(header.getParentHash()).orElse(null);
    if (previous == null) {
      return Optional.empty();
    }
    final MutableWorldState mutableWorldState =
        worldStateArchive.getMutable(previous.getStateRoot());
    final BlockHashLookup blockHashLookup = new BlockHashLookup(header, blockchain);
    for (final Transaction transaction : body.getTransactions()) {
      if (transaction.hash().equals(transactionHash)) {
        return Optional.of(
            action.performAction(
                transaction, header, blockchain, mutableWorldState, transactionProcessor));
      } else {
        final ProtocolSpec<?> spec = protocolSchedule.getByBlockNumber(header.getNumber());
        transactionProcessor.processTransaction(
            blockchain,
            mutableWorldState.updater(),
            header,
            transaction,
            spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header),
            blockHashLookup);
      }
    }
    return Optional.empty();
  }

  public <T> Optional<T> afterTransactionInBlock(
      final Hash blockHash, final Hash transactionHash, final Action<T> action) {
    return beforeTransactionInBlock(
        blockHash,
        transactionHash,
        (transaction, blockHeader, blockchain, worldState, transactionProcessor) -> {
          final ProtocolSpec<?> spec = protocolSchedule.getByBlockNumber(blockHeader.getNumber());
          transactionProcessor.processTransaction(
              blockchain,
              worldState.updater(),
              blockHeader,
              transaction,
              spec.getMiningBeneficiaryCalculator().calculateBeneficiary(blockHeader),
              new BlockHashLookup(blockHeader, blockchain));
          return action.performAction(
              transaction, blockHeader, blockchain, worldState, transactionProcessor);
        });
  }

  public interface Action<T> {

    T performAction(
        Transaction transaction,
        BlockHeader blockHeader,
        Blockchain blockchain,
        MutableWorldState worldState,
        TransactionProcessor transactionProcessor);
  }
}
