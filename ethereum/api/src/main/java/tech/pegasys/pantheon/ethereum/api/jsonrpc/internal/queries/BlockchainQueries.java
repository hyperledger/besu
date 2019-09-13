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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.api.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogsQuery;
import tech.pegasys.pantheon.ethereum.api.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.TransactionLocation;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

public class BlockchainQueries {

  private final WorldStateArchive worldStateArchive;
  private final Blockchain blockchain;

  public BlockchainQueries(final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
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
   * Retrieves the header hash of the block at the given height in the canonical chain.
   *
   * @param number The height of the block whose hash should be retrieved.
   * @return The hash of the block at the given height.
   */
  public Optional<Hash> getBlockHashByNumber(final long number) {
    return blockchain.getBlockHashByNumber(number);
  }

  /**
   * Return the block number of the head of the chain.
   *
   * @return The block number of the head of the chain.
   */
  public long headBlockNumber() {
    return blockchain.getChainHeadBlockNumber();
  }

  /**
   * Determines the block header for the address associated with this storage index.
   *
   * @param address The address of the account that owns the storage being queried.
   * @param storageIndex The storage index whose value is being retrieved.
   * @param blockNumber The blockNumber that is being queried.
   * @return The value at the storage index being queried.
   */
  public Optional<UInt256> storageAt(
      final Address address, final UInt256 storageIndex, final long blockNumber) {
    return fromAccount(
        address, blockNumber, account -> account.getStorageValue(storageIndex), UInt256.ZERO);
  }

  /**
   * Returns the balance of the given account at a specific block number.
   *
   * @param address The address of the account being queried.
   * @param blockNumber The block number being queried.
   * @return The balance of the account in Wei.
   */
  public Optional<Wei> accountBalance(final Address address, final long blockNumber) {
    return fromAccount(address, blockNumber, Account::getBalance, Wei.ZERO);
  }

  /**
   * Retrieves the code associated with the given account at a particular block number.
   *
   * @param address The account address being queried.
   * @param blockNumber The height of the block to be checked.
   * @return The code associated with this address.
   */
  public Optional<BytesValue> getCode(final Address address, final long blockNumber) {
    return fromAccount(address, blockNumber, Account::getCode, BytesValue.EMPTY);
  }

  /**
   * Returns the number of transactions in the block at the given height.
   *
   * @param blockNumber The height of the block being queried.
   * @return The number of transactions contained in the referenced block.
   */
  public Optional<Integer> getTransactionCount(final long blockNumber) {
    if (!withinValidRange(blockNumber)) {
      return Optional.empty();
    }
    return Optional.of(
        blockchain
            .getBlockHashByNumber(blockNumber)
            .flatMap(this::blockByHashWithTxHashes)
            .map(BlockWithMetadata::getTransactions)
            .map(List::size)
            .orElse(-1));
  }

  /**
   * Returns the number of transactions in the block with the given hash.
   *
   * @param blockHeaderHash The hash of the block being queried.
   * @return The number of transactions contained in the referenced block.
   */
  public Integer getTransactionCount(final Hash blockHeaderHash) {
    return blockchain
        .getBlockBody(blockHeaderHash)
        .map(body -> body.getTransactions().size())
        .orElse(-1);
  }

  /**
   * Returns the number of transactions sent from the given address in the block at the given
   * height.
   *
   * @param address The address whose sent transactions we want to count.
   * @param blockNumber The height of the block being queried.
   * @return The number of transactions sent from the given address.
   */
  public long getTransactionCount(final Address address, final long blockNumber) {
    return getWorldState(blockNumber)
        .map(worldState -> worldState.get(address))
        .map(Account::getNonce)
        .orElse(0L);
  }

  /**
   * Returns the number of transactions sent from the given address in the latest block.
   *
   * @param address The address whose sent transactions we want to count.
   * @return The number of transactions sent from the given address.
   */
  public long getTransactionCount(final Address address) {
    return getTransactionCount(address, headBlockNumber());
  }

  /**
   * Returns the number of ommers in the block at the given height.
   *
   * @param blockNumber The height of the block being queried.
   * @return The number of ommers in the referenced block.
   */
  public Optional<Integer> getOmmerCount(final long blockNumber) {
    return blockchain.getBlockHashByNumber(blockNumber).flatMap(this::getOmmerCount);
  }

  /**
   * Returns the number of ommers in the block at the given height.
   *
   * @param blockHeaderHash The hash of the block being queried.
   * @return The number of ommers in the referenced block.
   */
  public Optional<Integer> getOmmerCount(final Hash blockHeaderHash) {
    return blockchain.getBlockBody(blockHeaderHash).map(b -> b.getOmmers().size());
  }

  /**
   * Returns the number of ommers in the latest block.
   *
   * @return The number of ommers in the latest block.
   */
  public Optional<Integer> getOmmerCount() {
    return getOmmerCount(blockchain.getChainHeadHash());
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
   * Returns the ommer at the given index for the referenced block.
   *
   * @param blockNumber The block number identifying the block to be queried.
   * @param index The index of the ommer in the blocks ommers list.
   * @return The ommer at the given index belonging to the referenced block.
   */
  public Optional<BlockHeader> getOmmer(final long blockNumber, final int index) {
    return blockchain.getBlockHashByNumber(blockNumber).flatMap(hash -> getOmmer(hash, index));
  }

  /**
   * Returns the ommer at the given index for the latest block.
   *
   * @param index The index of the ommer in the blocks ommers list.
   * @return The ommer at the given index belonging to the latest block.
   */
  public Optional<BlockHeader> getOmmer(final int index) {
    return blockchain
        .getBlockHashByNumber(blockchain.getChainHeadBlockNumber())
        .flatMap(hash -> getOmmer(hash, index));
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
   * Given a block hash, returns the associated block with metadata and a list of transaction hashes
   * rather than full transactions.
   *
   * @param blockHeaderHash The hash of the target block's header.
   * @return The referenced block.
   */
  public Optional<BlockWithMetadata<Hash, Hash>> blockByHashWithTxHashes(
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
                                      final List<Hash> txs =
                                          body.getTransactions().stream()
                                              .map(Transaction::hash)
                                              .collect(Collectors.toList());
                                      final List<Hash> ommers =
                                          body.getOmmers().stream()
                                              .map(BlockHeader::getHash)
                                              .collect(Collectors.toList());
                                      final int size = new Block(header, body).calculateSize();
                                      return new BlockWithMetadata<>(header, txs, ommers, td, size);
                                    })));
  }

  /**
   * Given a block number, returns the associated block with metadata and a list of transaction
   * hashes rather than full transactions.
   *
   * @param blockNumber The height of the target block's header.
   * @return The referenced block.
   */
  public Optional<BlockWithMetadata<Hash, Hash>> blockByNumberWithTxHashes(final long blockNumber) {
    return blockchain.getBlockHashByNumber(blockNumber).flatMap(this::blockByHashWithTxHashes);
  }

  public Optional<BlockHeader> getBlockHeaderByNumber(final long number) {
    return blockchain.getBlockHeader(number);
  }

  /**
   * Returns the latest block with metadata and a list of transaction hashes rather than full
   * transactions.
   *
   * @return The latest block.
   */
  public Optional<BlockWithMetadata<Hash, Hash>> latestBlockWithTxHashes() {
    return this.blockByHashWithTxHashes(blockchain.getChainHeadHash());
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
   * Returns the transaction at the given index for the specified block.
   *
   * @param blockNumber The number of the block being queried.
   * @param txIndex The index of the transaction to return.
   * @return The transaction at the specified location.
   */
  public Optional<TransactionWithMetadata> transactionByBlockNumberAndIndex(
      final long blockNumber, final int txIndex) {
    checkArgument(txIndex >= 0);
    return blockchain
        .getBlockHeader(blockNumber)
        .map(header -> Optional.ofNullable(transactionByHeaderAndIndex(header, txIndex)))
        .orElse(Optional.empty());
  }

  /**
   * Returns the transaction at the given index for the specified block.
   *
   * @param blockHeaderHash The hash of the block being queried.
   * @param txIndex The index of the transaction to return.
   * @return The transaction at the specified location.
   */
  public Optional<TransactionWithMetadata> transactionByBlockHashAndIndex(
      final Hash blockHeaderHash, final int txIndex) {
    checkArgument(txIndex >= 0);
    return blockchain
        .getBlockHeader(blockHeaderHash)
        .map(header -> Optional.ofNullable(transactionByHeaderAndIndex(header, txIndex)))
        .orElse(Optional.empty());
  }

  /**
   * Helper method to return the transaction at the given index for the specified header, used by
   * getTransactionByBlock*AndIndex methods.
   *
   * @param header The block header.
   * @param txIndex The index of the transaction to return.
   * @return The transaction at the specified location.
   */
  private TransactionWithMetadata transactionByHeaderAndIndex(
      final BlockHeader header, final int txIndex) {
    final Hash blockHeaderHash = header.getHash();
    final BlockBody blockBody = blockchain.getBlockBody(blockHeaderHash).get();
    final List<Transaction> txs = blockBody.getTransactions();
    if (txIndex >= txs.size()) {
      return null;
    }
    return new TransactionWithMetadata(
        txs.get(txIndex), header.getNumber(), blockHeaderHash, txIndex);
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
        TransactionReceiptWithMetadata.create(
            transactionReceipt,
            transaction,
            transactionHash,
            location.getTransactionIndex(),
            gasUsed,
            blockhash,
            header.getNumber()));
  }

  /**
   * Retrieve logs from the range of blocks with optional filtering based on logger address and log
   * topics.
   *
   * @param fromBlockNumber The block number defining the first block in the search range
   *     (inclusive).
   * @param toBlockNumber The block number defining the last block in the search range (inclusive).
   * @param query Constraints on required topics by topic index. For a given index if the set of
   *     topics is non-empty, the topic at this index must match one of the values in the set.
   * @return The set of logs matching the given constraints.
   */
  public List<LogWithMetadata> matchingLogs(
      final long fromBlockNumber, final long toBlockNumber, final LogsQuery query) {
    if (fromBlockNumber > toBlockNumber || toBlockNumber > headBlockNumber()) {
      return Lists.newArrayList();
    }
    List<LogWithMetadata> matchingLogs = Lists.newArrayList();
    for (long blockNumber = fromBlockNumber; blockNumber <= toBlockNumber; blockNumber++) {
      final Hash blockhash = blockchain.getBlockHashByNumber(blockNumber).get();
      final boolean logHasBeenRemoved = !blockchain.blockIsOnCanonicalChain(blockhash);
      final List<TransactionReceipt> receipts = blockchain.getTxReceipts(blockhash).get();
      final List<Transaction> transaction =
          blockchain.getBlockBody(blockhash).get().getTransactions();
      matchingLogs =
          generateLogWithMetadata(
              receipts,
              blockNumber,
              query,
              blockhash,
              matchingLogs,
              transaction,
              logHasBeenRemoved);
    }
    return matchingLogs;
  }

  public List<LogWithMetadata> matchingLogs(final Hash blockhash, final LogsQuery query) {
    final List<LogWithMetadata> matchingLogs = Lists.newArrayList();
    Optional<BlockHeader> blockHeader = blockchain.getBlockHeader(blockhash);
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

  /**
   * Returns the world state for the corresponding block number
   *
   * @param blockNumber the block number
   * @return the world state at the block number
   */
  public Optional<MutableWorldState> getWorldState(final long blockNumber) {
    final Optional<BlockHeader> header = blockchain.getBlockHeader(blockNumber);
    return header.map(BlockHeader::getStateRoot).flatMap(worldStateArchive::getMutable);
  }

  private <T> Optional<T> fromWorldState(
      final long blockNumber, final Function<WorldState, T> getter) {
    if (!withinValidRange(blockNumber)) {
      return Optional.empty();
    }
    return getWorldState(blockNumber).map(getter);
  }

  private <T> Optional<T> fromAccount(
      final Address address,
      final long blockNumber,
      final Function<Account, T> getter,
      final T noAccountValue) {
    return fromWorldState(
        blockNumber,
        worldState ->
            Optional.ofNullable(worldState.get(address)).map(getter).orElse(noAccountValue));
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

  private boolean withinValidRange(final long blockNumber) {
    return blockNumber <= headBlockNumber() && blockNumber >= BlockHeader.GENESIS_BLOCK_NUMBER;
  }
}
