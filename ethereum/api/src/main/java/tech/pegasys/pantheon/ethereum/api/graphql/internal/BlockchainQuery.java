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
package tech.pegasys.pantheon.ethereum.api.graphql.internal;

import tech.pegasys.pantheon.ethereum.api.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogsQuery;
import tech.pegasys.pantheon.ethereum.api.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.TransactionLocation;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

public class BlockchainQuery {

  private final WorldStateArchive worldStateArchive;
  private final Blockchain blockchain;

  public BlockchainQuery(final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
  }

  public Blockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  /**
   * Returns the ommer at the given index for the referenced block.
   *
   * @param blockHeaderHash The hash of the block to be queried.
   * @param index The index of the ommer in the blocks ommers list.
   * @return The ommer at the given index belonging to the referenced block.
   */
  public Optional<BlockHeader> getOmmer(final Hash blockHeaderHash, final int index) {
    return blockchain.getBlockBody(blockHeaderHash).map(blockBody -> getOmmer(blockBody, index));
  }

  private BlockHeader getOmmer(final BlockBody blockBody, final int index) {
    final List<BlockHeader> ommers = blockBody.getOmmers();
    if (ommers.size() > index) {
      return ommers.get(index);
    } else {
      return null;
    }
  }

  /**
   * Given a block hash, returns the associated block augmented with metadata.
   *
   * @param blockHeaderHash The hash of the target block's header.
   * @return The referenced block.
   */
  public Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> blockByHash(
      final Hash blockHeaderHash) {
    return blockchain
        .getBlockHeader(blockHeaderHash)
        .flatMap(
            header ->
                blockchain
                    .getBlockBody(blockHeaderHash)
                    .flatMap(
                        body ->
                            blockchain
                                .getTotalDifficultyByHash(blockHeaderHash)
                                .map(
                                    (td) -> {
                                      final List<Transaction> txs = body.getTransactions();
                                      final List<TransactionWithMetadata> formattedTxs =
                                          formatTransactions(
                                              txs, header.getNumber(), blockHeaderHash);
                                      final List<Hash> ommers =
                                          body.getOmmers().stream()
                                              .map(BlockHeader::getHash)
                                              .collect(Collectors.toList());
                                      final int size = new Block(header, body).calculateSize();
                                      return new BlockWithMetadata<>(
                                          header, formattedTxs, ommers, td, size);
                                    })));
  }

  /**
   * Given a block number, returns the associated block augmented with metadata.
   *
   * @param number The height of the target block.
   * @return The referenced block.
   */
  public Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> blockByNumber(
      final long number) {
    return blockchain.getBlockHashByNumber(number).flatMap(this::blockByHash);
  }

  /**
   * Returns the latest block augmented with metadata.
   *
   * @return The latest block.
   */
  public Optional<BlockWithMetadata<TransactionWithMetadata, Hash>> latestBlock() {
    return this.blockByHash(blockchain.getChainHeadHash());
  }

  /**
   * Given a transaction hash, returns the associated transaction.
   *
   * @param transactionHash The hash of the target transaction.
   * @return The transaction associated with the given hash.
   */
  public Optional<TransactionWithMetadata> transactionByHash(final Hash transactionHash) {
    final Optional<TransactionLocation> maybeLocation =
        blockchain.getTransactionLocation(transactionHash);
    if (!maybeLocation.isPresent()) {
      return Optional.empty();
    }
    final TransactionLocation loc = maybeLocation.get();
    final Hash blockHash = loc.getBlockHash();
    final BlockHeader header = blockchain.getBlockHeader(blockHash).get();
    final Transaction transaction = blockchain.getTransactionByHash(transactionHash).get();
    return Optional.of(
        new TransactionWithMetadata(
            transaction, header.getNumber(), blockHash, loc.getTransactionIndex()));
  }

  /**
   * Returns the transaction receipt associated with the given transaction hash.
   *
   * @param transactionHash The hash of the transaction that corresponds to the receipt to retrieve.
   * @return The transaction receipt associated with the referenced transaction.
   */
  public Optional<TransactionReceiptWithMetadata> transactionReceiptByTransactionHash(
      final Hash transactionHash) {
    final Optional<TransactionLocation> maybeLocation =
        blockchain.getTransactionLocation(transactionHash);
    if (!maybeLocation.isPresent()) {
      return Optional.empty();
    }
    final TransactionLocation location = maybeLocation.get();
    final BlockBody blockBody = blockchain.getBlockBody(location.getBlockHash()).get();
    final Transaction transaction = blockBody.getTransactions().get(location.getTransactionIndex());

    final Hash blockhash = location.getBlockHash();
    final BlockHeader header = blockchain.getBlockHeader(blockhash).get();
    final List<TransactionReceipt> transactionReceipts = blockchain.getTxReceipts(blockhash).get();
    final TransactionReceipt transactionReceipt =
        transactionReceipts.get(location.getTransactionIndex());

    long gasUsed = transactionReceipt.getCumulativeGasUsed();
    if (location.getTransactionIndex() > 0) {
      gasUsed =
          gasUsed
              - transactionReceipts.get(location.getTransactionIndex() - 1).getCumulativeGasUsed();
    }

    return Optional.of(
        new TransactionReceiptWithMetadata(
            transactionReceipt,
            transaction,
            transactionHash,
            location.getTransactionIndex(),
            gasUsed,
            blockhash,
            header.getNumber()));
  }

  /**
   * Returns the world state for the corresponding block number
   *
   * @param blockNumber the block number
   * @return the world state at the block number
   */
  public Optional<WorldState> getWorldState(final long blockNumber) {
    final Optional<BlockHeader> header = blockchain.getBlockHeader(blockNumber);
    return header
        .map(BlockHeader::getStateRoot)
        .flatMap(worldStateArchive::getMutable)
        .map(mws -> mws); // to satisfy typing
  }

  private List<TransactionWithMetadata> formatTransactions(
      final List<Transaction> txs, final long blockNumber, final Hash blockHash) {
    final int count = txs.size();
    final List<TransactionWithMetadata> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      result.add(new TransactionWithMetadata(txs.get(i), blockNumber, blockHash, i));
    }
    return result;
  }

  public List<LogWithMetadata> matchingLogs(final Hash blockhash, final LogsQuery query) {
    final List<LogWithMetadata> matchingLogs = Lists.newArrayList();
    final Optional<BlockHeader> blockHeader = blockchain.getBlockHeader(blockhash);
    if (!blockHeader.isPresent()) {
      return matchingLogs;
    }
    final List<TransactionReceipt> receipts = blockchain.getTxReceipts(blockhash).get();
    final List<Transaction> transaction =
        blockchain.getBlockBody(blockhash).get().getTransactions();
    final long number = blockHeader.get().getNumber();
    final boolean logHasBeenRemoved = !blockchain.blockIsOnCanonicalChain(blockhash);
    return generateLogWithMetadata(
        receipts, number, query, blockhash, matchingLogs, transaction, logHasBeenRemoved);
  }

  private List<LogWithMetadata> generateLogWithMetadata(
      final List<TransactionReceipt> receipts,
      final long number,
      final LogsQuery query,
      final Hash blockhash,
      final List<LogWithMetadata> matchingLogs,
      final List<Transaction> transaction,
      final boolean removed) {
    for (int transactionIndex = 0; transactionIndex < receipts.size(); ++transactionIndex) {
      final TransactionReceipt receipt = receipts.get(transactionIndex);
      for (int logIndex = 0; logIndex < receipt.getLogs().size(); ++logIndex) {
        if (query.matches(receipt.getLogs().get(logIndex))) {
          final LogWithMetadata logWithMetaData =
              new LogWithMetadata(
                  logIndex,
                  number,
                  blockhash,
                  transaction.get(transactionIndex).hash(),
                  transactionIndex,
                  receipts.get(transactionIndex).getLogs().get(logIndex).getLogger(),
                  receipts.get(transactionIndex).getLogs().get(logIndex).getData(),
                  receipts.get(transactionIndex).getLogs().get(logIndex).getTopics(),
                  removed);
          matchingLogs.add(logWithMetaData);
        }
      }
    }
    return matchingLogs;
  }

  public static List<LogWithMetadata> generateLogWithMetadataForTransaction(
      final TransactionReceipt receipt,
      final long number,
      final Hash blockhash,
      final Hash transactionHash,
      final int transactionIndex,
      final boolean removed) {

    final List<LogWithMetadata> logs = new ArrayList<>();
    for (int logIndex = 0; logIndex < receipt.getLogs().size(); ++logIndex) {

      final LogWithMetadata logWithMetaData =
          new LogWithMetadata(
              logIndex,
              number,
              blockhash,
              transactionHash,
              transactionIndex,
              receipt.getLogs().get(logIndex).getLogger(),
              receipt.getLogs().get(logIndex).getData(),
              receipt.getLogs().get(logIndex).getTopics(),
              removed);
      logs.add(logWithMetaData);
    }

    return logs;
  }
}
