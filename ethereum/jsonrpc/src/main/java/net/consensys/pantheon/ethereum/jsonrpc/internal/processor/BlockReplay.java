package net.consensys.pantheon.ethereum.jsonrpc.internal.processor;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor;

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
            spec.getMiningBeneficiaryCalculator().calculateBeneficiary(header));
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
              spec.getMiningBeneficiaryCalculator().calculateBeneficiary(blockHeader));
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
