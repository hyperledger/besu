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
package org.hyperledger.besu.ethereum.api.query;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher.BLOCKS_PER_BLOOM_CACHE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockchainQueries {
  private static final Logger LOG = LoggerFactory.getLogger(BlockchainQueries.class);

  private final WorldStateArchive worldStateArchive;
  private final Blockchain blockchain;
  private final Optional<Path> cachePath;
  private final Optional<TransactionLogBloomCacher> transactionLogBloomCacher;
  private final ApiConfiguration apiConfig;

  public BlockchainQueries(final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    this(blockchain, worldStateArchive, Optional.empty(), Optional.empty());
  }

  public BlockchainQueries(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final EthScheduler scheduler) {
    this(blockchain, worldStateArchive, Optional.empty(), Optional.ofNullable(scheduler));
  }

  public BlockchainQueries(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final Optional<Path> cachePath,
      final Optional<EthScheduler> scheduler) {
    this(
        blockchain,
        worldStateArchive,
        cachePath,
        scheduler,
        ImmutableApiConfiguration.builder().build());
  }

  public BlockchainQueries(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final Optional<Path> cachePath,
      final Optional<EthScheduler> scheduler,
      final ApiConfiguration apiConfig) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.cachePath = cachePath;
    this.transactionLogBloomCacher =
        (cachePath.isPresent() && scheduler.isPresent())
            ? Optional.of(
                new TransactionLogBloomCacher(blockchain, cachePath.get(), scheduler.get()))
            : Optional.empty();
    this.apiConfig = apiConfig;
  }

  public Blockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  public Optional<TransactionLogBloomCacher> getTransactionLogBloomCacher() {
    return transactionLogBloomCacher;
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
   * Return the header of the last finalized block.
   *
   * @return The header of the last finalized block.
   */
  public Optional<BlockHeader> finalizedBlockHeader() {
    return blockchain.getFinalized().flatMap(blockchain::getBlockHeader);
  }

  /**
   * Return the header of the last safe block.
   *
   * @return The header of the last safe block.
   */
  public Optional<BlockHeader> safeBlockHeader() {
    return blockchain.getSafeBlock().flatMap(blockchain::getBlockHeader);
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
    final Hash blockHash =
        getBlockHeaderByNumber(blockNumber).map(BlockHeader::getHash).orElse(Hash.EMPTY);

    return storageAt(address, storageIndex, blockHash);
  }

  /**
   * Determines the block header for the address associated with this storage index.
   *
   * @param address The address of the account that owns the storage being queried.
   * @param storageIndex The storage index whose value is being retrieved.
   * @param blockHash The blockHash that is being queried.
   * @return The value at the storage index being queried.
   */
  public Optional<UInt256> storageAt(
      final Address address, final UInt256 storageIndex, final Hash blockHash) {
    return fromAccount(
        address, blockHash, account -> account.getStorageValue(storageIndex), UInt256.ZERO);
  }

  /**
   * Returns the balance of the given account at a specific block number.
   *
   * @param address The address of the account being queried.
   * @param blockNumber The block number being queried.
   * @return The balance of the account in Wei.
   */
  public Optional<Wei> accountBalance(final Address address, final long blockNumber) {
    final Hash blockHash =
        getBlockHeaderByNumber(blockNumber).map(BlockHeader::getHash).orElse(Hash.EMPTY);

    return accountBalance(address, blockHash);
  }

  /**
   * Returns the balance of the given account at a specific block hash.
   *
   * @param address The address of the account being queried.
   * @param blockHash The block hash being queried.
   * @return The balance of the account in Wei.
   */
  public Optional<Wei> accountBalance(final Address address, final Hash blockHash) {
    return fromAccount(address, blockHash, Account::getBalance, Wei.ZERO);
  }

  /**
   * Retrieves the code associated with the given account at a particular block number.
   *
   * @param address The account address being queried.
   * @param blockNumber The height of the block to be checked.
   * @return The code associated with this address.
   */
  public Optional<Bytes> getCode(final Address address, final long blockNumber) {
    final Hash blockHash =
        getBlockHeaderByNumber(blockNumber).map(BlockHeader::getHash).orElse(Hash.EMPTY);

    return getCode(address, blockHash);
  }

  /**
   * Retrieves the code associated with the given account at a particular block hash.
   *
   * @param address The account address being queried.
   * @param blockHash The hash of the block to be checked.
   * @return The code associated with this address.
   */
  public Optional<Bytes> getCode(final Address address, final Hash blockHash) {
    return fromAccount(address, blockHash, Account::getCode, Bytes.EMPTY);
  }

  /**
   * Returns the number of transactions in the block at the given height.
   *
   * @param blockNumber The height of the block being queried.
   * @return The number of transactions contained in the referenced block.
   */
  public Optional<Integer> getTransactionCount(final long blockNumber) {
    if (outsideBlockchainRange(blockNumber)) {
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
    final Hash blockHash =
        getBlockHeaderByNumber(blockNumber).map(BlockHeader::getHash).orElse(Hash.EMPTY);

    return getTransactionCount(address, blockHash);
  }

  /**
   * Returns the number of transactions sent from the given address in the block at the given hash.
   *
   * @param address The address whose sent transactions we want to count.
   * @param blockHash The hash of the block being queried.
   * @return The number of transactions sent from the given address.
   */
  public long getTransactionCount(final Address address, final Hash blockHash) {
    return getWorldState(blockHash)
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
                                    td -> {
                                      final List<Transaction> txs = body.getTransactions();
                                      final List<TransactionWithMetadata> formattedTxs =
                                          formatTransactions(
                                              txs,
                                              header.getNumber(),
                                              header.getBaseFee(),
                                              blockHeaderHash);
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
                                    td -> {
                                      final List<Hash> txs =
                                          body.getTransactions().stream()
                                              .map(Transaction::getHash)
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

  public Optional<BlockHeader> getBlockHeaderByHash(final Hash hash) {
    return blockchain.getBlockHeader(hash);
  }

  public Optional<BlockHeader> getBlockHeaderByNumber(final long number) {
    return blockchain.getBlockHeader(number);
  }

  public boolean blockIsOnCanonicalChain(final Hash hash) {
    return blockchain.blockIsOnCanonicalChain(hash);
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
    if (maybeLocation.isEmpty()) {
      return Optional.empty();
    }
    final TransactionLocation loc = maybeLocation.get();
    final Hash blockHash = loc.getBlockHash();
    // getTransactionLocation should not return if the TX or block doesn't exist, so throwing
    // on a missing optional is appropriate.
    final BlockHeader header = blockchain.getBlockHeader(blockHash).orElseThrow();
    final Transaction transaction = blockchain.getTransactionByHash(transactionHash).orElseThrow();
    return Optional.of(
        new TransactionWithMetadata(
            transaction,
            header.getNumber(),
            header.getBaseFee(),
            blockHash,
            loc.getTransactionIndex()));
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
        .map(header -> transactionByHeaderAndIndex(header, txIndex));
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
        .map(header -> transactionByHeaderAndIndex(header, txIndex));
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
    // headers should not exist w/o bodies, so not being present is exceptional
    final BlockBody blockBody = blockchain.getBlockBody(blockHeaderHash).orElseThrow();
    final List<Transaction> txs = blockBody.getTransactions();
    if (txIndex >= txs.size()) {
      return null;
    }
    return new TransactionWithMetadata(
        txs.get(txIndex), header.getNumber(), header.getBaseFee(), blockHeaderHash, txIndex);
  }

  public Optional<TransactionLocation> transactionLocationByHash(final Hash transactionHash) {
    return blockchain.getTransactionLocation(transactionHash);
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
    if (maybeLocation.isEmpty()) {
      return Optional.empty();
    }
    // getTransactionLocation should not return if the TX or block doesn't exist, so throwing
    // on a missing optional is appropriate.
    final TransactionLocation location = maybeLocation.get();
    final Block block = blockchain.getBlockByHash(location.getBlockHash()).orElseThrow();
    final Transaction transaction =
        block.getBody().getTransactions().get(location.getTransactionIndex());

    final Hash blockhash = location.getBlockHash();
    final BlockHeader header = block.getHeader();
    final List<TransactionReceipt> transactionReceipts =
        blockchain.getTxReceipts(blockhash).orElseThrow();
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
            header.getBaseFee(),
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
   * @param isQueryAlive Whether or not the backend query should stay alive.
   * @return The set of logs matching the given constraints.
   */
  public List<LogWithMetadata> matchingLogs(
      final long fromBlockNumber,
      final long toBlockNumber,
      final LogsQuery query,
      final Supplier<Boolean> isQueryAlive) {
    try {
      final List<LogWithMetadata> result = new ArrayList<>();
      final long startSegment = fromBlockNumber / BLOCKS_PER_BLOOM_CACHE;
      final long endSegment = toBlockNumber / BLOCKS_PER_BLOOM_CACHE;
      long currentStep = fromBlockNumber;
      for (long segment = startSegment; segment <= endSegment; segment++) {
        final long thisSegment = segment;
        final long thisStep = currentStep;
        final long nextStep = (segment + 1) * BLOCKS_PER_BLOOM_CACHE;
        BackendQuery.stopIfExpired(isQueryAlive);
        result.addAll(
            cachePath
                .map(path -> path.resolve("logBloom-" + thisSegment + ".cache"))
                .filter(Files::isRegularFile)
                .map(
                    cacheFile -> {
                      try {
                        return matchingLogsCached(
                            thisSegment * BLOCKS_PER_BLOOM_CACHE,
                            thisStep % BLOCKS_PER_BLOOM_CACHE,
                            Math.min(toBlockNumber, nextStep - 1) % BLOCKS_PER_BLOOM_CACHE,
                            query,
                            cacheFile,
                            isQueryAlive);
                      } catch (final Exception e) {
                        throw new RuntimeException(e);
                      }
                    })
                .orElseGet(
                    () ->
                        matchingLogsUncached(
                            thisStep,
                            Math.min(toBlockNumber, Math.min(toBlockNumber, nextStep - 1)),
                            query,
                            isQueryAlive)));
        currentStep = nextStep;
      }
      return result;
    } catch (final Exception e) {
      throw new IllegalStateException("Error retrieving matching logs", e);
    }
  }

  private List<LogWithMetadata> matchingLogsUncached(
      final long fromBlockNumber,
      final long toBlockNumber,
      final LogsQuery query,
      final Supplier<Boolean> isQueryAlive) {
    // rangeClosed handles the inverted from/to situations automatically with zero results.
    return LongStream.rangeClosed(fromBlockNumber, toBlockNumber)
        .mapToObj(blockchain::getBlockHeader)
        // Use takeWhile instead of clamping on toBlockNumber/headBlockNumber because it may get an
        // extra block or two for a query that has a toBlockNumber past chain head.  Similarly this
        // handles the case when fromBlockNumber is past chain head.
        .takeWhile(Optional::isPresent)
        .map(Optional::get)
        .filter(header -> query.couldMatch(header.getLogsBloom(true)))
        .flatMap(header -> matchingLogs(header.getHash(), query, isQueryAlive).stream())
        .collect(Collectors.toList());
  }

  private List<LogWithMetadata> matchingLogsCached(
      final long segmentStart,
      final long offset,
      final long endOffset,
      final LogsQuery query,
      final Path cacheFile,
      final Supplier<Boolean> isQueryAlive)
      throws Exception {
    final List<LogWithMetadata> results = new ArrayList<>();
    try (final RandomAccessFile raf = new RandomAccessFile(cacheFile.toFile(), "r")) {
      raf.seek(offset * 256);
      final byte[] bloomBuff = new byte[256];
      final Bytes bytesValue = Bytes.wrap(bloomBuff);
      for (long pos = offset; pos <= endOffset; pos++) {
        BackendQuery.stopIfExpired(isQueryAlive);
        try {
          raf.readFully(bloomBuff);
        } catch (final EOFException e) {
          results.addAll(
              matchingLogsUncached(
                  segmentStart + pos, segmentStart + endOffset, query, isQueryAlive));
          break;
        }
        final LogsBloomFilter logsBloom = new LogsBloomFilter(bytesValue);
        if (query.couldMatch(logsBloom)) {
          results.addAll(
              matchingLogs(
                  blockchain.getBlockHashByNumber(segmentStart + pos).orElseThrow(),
                  query,
                  isQueryAlive));
        }
      }
    } catch (final IOException e) {
      e.printStackTrace(System.out);
      LOG.error("Error reading cached log blooms", e);
    }
    return results;
  }

  public List<LogWithMetadata> matchingLogs(
      final Hash blockHash, final LogsQuery query, final Supplier<Boolean> isQueryAlive) {
    try {
      final Optional<BlockHeader> blockHeader =
          BackendQuery.runIfAlive(
              "matchingLogs - getBlockHeader",
              () -> blockchain.getBlockHeader(blockHash),
              isQueryAlive);
      if (blockHeader.isEmpty()) {
        return Collections.emptyList();
      }
      // receipts and transactions should exist if the header exists, so throwing is ok.
      final List<TransactionReceipt> receipts =
          BackendQuery.runIfAlive(
              "matchingLogs - getTxReceipts",
              () -> blockchain.getTxReceipts(blockHash).orElseThrow(),
              isQueryAlive);
      final List<Transaction> transactions =
          BackendQuery.runIfAlive(
              "matchingLogs - getBlockBody",
              () -> blockchain.getBlockBody(blockHash).orElseThrow().getTransactions(),
              isQueryAlive);
      final long number = blockHeader.get().getNumber();
      final boolean removed =
          BackendQuery.runIfAlive(
              "matchingLogs - blockIsOnCanonicalChain",
              () -> !blockchain.blockIsOnCanonicalChain(blockHash),
              isQueryAlive);
      return IntStream.range(0, receipts.size())
          .mapToObj(
              i -> {
                try {
                  BackendQuery.stopIfExpired(isQueryAlive);
                  return LogWithMetadata.generate(
                      receipts.get(i),
                      number,
                      blockHash,
                      transactions.get(i).getHash(),
                      i,
                      removed);
                } catch (final Exception e) {
                  throw new RuntimeException(e);
                }
              })
          .flatMap(Collection::stream)
          .filter(query::matches)
          .collect(Collectors.toList());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the world state for the corresponding block number
   *
   * @param blockNumber the block number
   * @return the world state at the block number
   */
  public Optional<WorldState> getWorldState(final long blockNumber) {
    final Hash blockHash =
        getBlockHeaderByNumber(blockNumber).map(BlockHeader::getHash).orElse(Hash.EMPTY);

    return getWorldState(blockHash);
  }

  /**
   * Returns the world state for the corresponding block hash
   *
   * @param blockHash the block hash
   * @return the world state at the block hash
   */
  public Optional<WorldState> getWorldState(final Hash blockHash) {
    final Optional<BlockHeader> header = blockchain.getBlockHeader(blockHash);
    return header.flatMap(
        blockHeader ->
            worldStateArchive.getMutable(blockHeader.getStateRoot(), blockHeader.getHash(), false));
  }

  public Optional<Long> gasPrice() {
    final long blockHeight = headBlockNumber();
    final long[] gasCollection =
        LongStream.range(Math.max(0, blockHeight - apiConfig.getGasPriceBlocks()), blockHeight)
            .mapToObj(
                l ->
                    blockchain
                        .getBlockByNumber(l)
                        .map(Block::getBody)
                        .map(BlockBody::getTransactions)
                        .orElseThrow(
                            () -> new IllegalStateException("Could not retrieve block #" + l)))
            .flatMap(Collection::stream)
            .filter(t -> t.getGasPrice().isPresent())
            .mapToLong(t -> t.getGasPrice().get().toLong())
            .sorted()
            .toArray();
    return (gasCollection == null || gasCollection.length == 0)
        ? Optional.empty()
        : Optional.of(
            Math.max(
                apiConfig.getGasPriceMin(),
                Math.min(
                    apiConfig.getGasPriceMax(),
                    gasCollection[
                        Math.min(
                            gasCollection.length - 1,
                            (int) ((gasCollection.length) * apiConfig.getGasPriceFraction()))])));
  }

  private <T> Optional<T> fromWorldState(
      final Hash blockHash, final Function<WorldState, T> getter) {
    return getWorldState(blockHash).map(getter);
  }

  private <T> Optional<T> fromAccount(
      final Address address,
      final Hash blockHash,
      final Function<Account, T> getter,
      final T noAccountValue) {
    return fromWorldState(
        blockHash,
        worldState ->
            Optional.ofNullable(worldState.get(address)).map(getter).orElse(noAccountValue));
  }

  private List<TransactionWithMetadata> formatTransactions(
      final List<Transaction> txs,
      final long blockNumber,
      final Optional<Wei> baseFee,
      final Hash blockHash) {
    final int count = txs.size();
    final List<TransactionWithMetadata> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      result.add(new TransactionWithMetadata(txs.get(i), blockNumber, baseFee, blockHash, i));
    }
    return result;
  }

  private boolean outsideBlockchainRange(final long blockNumber) {
    return blockNumber > headBlockNumber() || blockNumber < BlockHeader.GENESIS_BLOCK_NUMBER;
  }
}
