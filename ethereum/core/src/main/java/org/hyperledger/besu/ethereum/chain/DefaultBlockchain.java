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
package org.hyperledger.besu.ethereum.chain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.InvalidConfigurationException;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;

public class DefaultBlockchain implements MutableBlockchain {

  protected final BlockchainStorage blockchainStorage;

  private final Subscribers<BlockAddedObserver> blockAddedObservers = Subscribers.create();

  private volatile BlockHeader chainHeader;
  private volatile UInt256 totalDifficulty;
  private volatile int chainHeadTransactionCount;
  private volatile int chainHeadOmmerCount;

  private DefaultBlockchain(
      final Optional<Block> genesisBlock,
      final BlockchainStorage blockchainStorage,
      final MetricsSystem metricsSystem) {
    checkNotNull(genesisBlock);
    checkNotNull(blockchainStorage);
    checkNotNull(metricsSystem);

    this.blockchainStorage = blockchainStorage;
    genesisBlock.ifPresent(this::setGenesis);

    final Hash chainHead = blockchainStorage.getChainHead().get();
    chainHeader = blockchainStorage.getBlockHeader(chainHead).get();
    totalDifficulty = blockchainStorage.getTotalDifficulty(chainHead).get();
    final BlockBody chainHeadBody = blockchainStorage.getBlockBody(chainHead).get();
    chainHeadTransactionCount = chainHeadBody.getTransactions().size();
    chainHeadOmmerCount = chainHeadBody.getOmmers().size();

    metricsSystem.createLongGauge(
        BesuMetricCategory.ETHEREUM,
        "blockchain_height",
        "The current height of the canonical chain",
        this::getChainHeadBlockNumber);
    metricsSystem.createLongGauge(
        BesuMetricCategory.BLOCKCHAIN,
        "difficulty_total",
        "Total difficulty of the chainhead",
        () ->
            BytesValues.asUnsignedBigInteger(this.getChainHead().getTotalDifficulty().getBytes())
                .longValue());

    metricsSystem.createLongGauge(
        BesuMetricCategory.BLOCKCHAIN,
        "chain_head_timestamp",
        "Timestamp from the current chain head",
        () -> getChainHeadHeader().getTimestamp());

    metricsSystem.createLongGauge(
        BesuMetricCategory.BLOCKCHAIN,
        "chain_head_gas_used",
        "Gas used by the current chain head block",
        () -> getChainHeadHeader().getGasUsed());

    metricsSystem.createLongGauge(
        BesuMetricCategory.BLOCKCHAIN,
        "chain_head_gas_limit",
        "Block gas limit of the current chain head block",
        () -> getChainHeadHeader().getGasLimit());

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.BLOCKCHAIN,
        "chain_head_transaction_count",
        "Number of transactions in the current chain head block",
        () -> chainHeadTransactionCount);

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.BLOCKCHAIN,
        "chain_head_ommer_count",
        "Number of ommers in the current chain head block",
        () -> chainHeadOmmerCount);
  }

  public static MutableBlockchain createMutable(
      final Block genesisBlock,
      final BlockchainStorage blockchainStorage,
      final MetricsSystem metricsSystem) {
    checkNotNull(genesisBlock);
    return new DefaultBlockchain(Optional.of(genesisBlock), blockchainStorage, metricsSystem);
  }

  public static Blockchain create(
      final BlockchainStorage blockchainStorage, final MetricsSystem metricsSystem) {
    checkArgument(
        validateStorageNonEmpty(blockchainStorage), "Cannot create Blockchain from empty storage");
    return new DefaultBlockchain(Optional.empty(), blockchainStorage, metricsSystem);
  }

  private static boolean validateStorageNonEmpty(final BlockchainStorage blockchainStorage) {
    // Run a few basic checks to make sure data looks available and consistent
    final Optional<Hash> maybeHead = blockchainStorage.getChainHead();
    if (maybeHead.isEmpty()) {
      return false;
    }
    final Optional<Hash> genesisHash =
        blockchainStorage.getBlockHash(BlockHeader.GENESIS_BLOCK_NUMBER);
    if (genesisHash.isEmpty()) {
      return false;
    }
    final Optional<UInt256> td = blockchainStorage.getTotalDifficulty(maybeHead.get());
    if (td.isEmpty()) {
      return false;
    }

    return true;
  }

  @Override
  public ChainHead getChainHead() {
    return new ChainHead(chainHeader.getHash(), totalDifficulty, chainHeader.getNumber());
  }

  @Override
  public Hash getChainHeadHash() {
    return chainHeader.getHash();
  }

  @Override
  public long getChainHeadBlockNumber() {
    return chainHeader.getNumber();
  }

  @Override
  public BlockHeader getChainHeadHeader() {
    return chainHeader;
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final long blockNumber) {
    return blockchainStorage.getBlockHash(blockNumber).flatMap(blockchainStorage::getBlockHeader);
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final Hash blockHeaderHash) {
    return blockchainStorage.getBlockHeader(blockHeaderHash);
  }

  @Override
  public Optional<BlockBody> getBlockBody(final Hash blockHeaderHash) {
    return blockchainStorage.getBlockBody(blockHeaderHash);
  }

  @Override
  public Optional<List<TransactionReceipt>> getTxReceipts(final Hash blockHeaderHash) {
    return blockchainStorage.getTransactionReceipts(blockHeaderHash);
  }

  @Override
  public Optional<Hash> getBlockHashByNumber(final long number) {
    return blockchainStorage.getBlockHash(number);
  }

  @Override
  public Optional<UInt256> getTotalDifficultyByHash(final Hash blockHeaderHash) {
    return blockchainStorage.getTotalDifficulty(blockHeaderHash);
  }

  @Override
  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return blockchainStorage
        .getTransactionLocation(transactionHash)
        .flatMap(
            l ->
                blockchainStorage
                    .getBlockBody(l.getBlockHash())
                    .map(b -> b.getTransactions().get(l.getTransactionIndex())));
  }

  @Override
  public Optional<TransactionLocation> getTransactionLocation(final Hash transactionHash) {
    return blockchainStorage.getTransactionLocation(transactionHash);
  }

  @Override
  public synchronized void appendBlock(final Block block, final List<TransactionReceipt> receipts) {
    checkArgument(
        block.getBody().getTransactions().size() == receipts.size(),
        "Supplied receipts do not match block transactions.");
    if (blockIsAlreadyTracked(block)) {
      return;
    }
    checkArgument(blockIsConnected(block), "Attempt to append non-connected block.");

    final BlockAddedEvent blockAddedEvent =
        appendBlockHelper(new BlockWithReceipts(block, receipts));
    notifyBlockAdded(blockAddedEvent);
  }

  private BlockAddedEvent appendBlockHelper(final BlockWithReceipts blockWithReceipts) {
    final var block = blockWithReceipts.getBlock();
    final var receipts = blockWithReceipts.getReceipts();
    final Hash hash = block.getHash();
    final UInt256 td = calculateTotalDifficulty(block);

    final BlockchainStorage.Updater updater = blockchainStorage.updater();

    updater.putBlockHeader(hash, block.getHeader());
    updater.putBlockBody(hash, block.getBody());
    updater.putTransactionReceipts(hash, receipts);
    updater.putTotalDifficulty(hash, td);

    // Update canonical chain data
    final BlockAddedEvent blockAddedEvent =
        updateCanonicalChainData(updater, blockWithReceipts, td);

    updater.commit();
    if (blockAddedEvent.isNewCanonicalHead()) {
      updateCacheForNewCanonicalHead(block, td);
    }

    return blockAddedEvent;
  }

  private UInt256 calculateTotalDifficulty(final Block block) {
    if (block.getHeader().getNumber() == BlockHeader.GENESIS_BLOCK_NUMBER) {
      return block.getHeader().getDifficulty();
    }

    final UInt256 parentTargetDifficulty =
        blockchainStorage
            .getTotalDifficulty(block.getHeader().getParentHash())
            .orElseThrow(
                () -> new IllegalStateException("Blockchain is missing total difficulty data."));
    return block.getHeader().getDifficulty().plus(parentTargetDifficulty);
  }

  private BlockAddedEvent updateCanonicalChainData(
      final BlockchainStorage.Updater updater,
      final BlockWithReceipts blockWithReceipts,
      final UInt256 totalDifficulty) {
    final var newBlock = blockWithReceipts.getBlock();
    final Hash chainHead = blockchainStorage.getChainHead().orElse(null);
    if (newBlock.getHeader().getNumber() != BlockHeader.GENESIS_BLOCK_NUMBER && chainHead == null) {
      throw new IllegalStateException("Blockchain is missing chain head.");
    }

    final Hash newBlockHash = newBlock.getHash();
    try {
      if (chainHead == null || newBlock.getHeader().getParentHash().equals(chainHead)) {
        // This block advances the chain, update the chain head
        updater.putBlockHash(newBlock.getHeader().getNumber(), newBlockHash);
        updater.setChainHead(newBlockHash);
        indexTransactionForBlock(updater, newBlockHash, newBlock.getBody().getTransactions());
        return BlockAddedEvent.createForHeadAdvancement(
            newBlock, blockWithReceipts.getLogsWithMetadata(false));
      } else if (totalDifficulty.compareTo(blockchainStorage.getTotalDifficulty(chainHead).get())
          > 0) {
        // New block represents a chain reorganization
        return handleChainReorg(updater, blockWithReceipts);
      } else {
        // New block represents a fork
        return handleFork(updater, newBlock);
      }
    } catch (final NoSuchElementException e) {
      // Any Optional.get() calls in this block should be present, missing data means data
      // corruption or a bug.
      updater.rollback();
      throw new IllegalStateException("Blockchain is missing data that should be present.", e);
    }
  }

  private BlockAddedEvent handleFork(final BlockchainStorage.Updater updater, final Block fork) {
    final Collection<Hash> forkHeads = blockchainStorage.getForkHeads();

    // Check to see if this block advances any existing fork.
    final Hash parentHash = fork.getHeader().getParentHash();
    final Optional<Hash> parent =
        forkHeads.stream().filter(head -> head.equals(parentHash)).findAny();
    // This block will replace its parent
    parent.ifPresent(forkHeads::remove);

    forkHeads.add(fork.getHash());

    updater.setForkHeads(forkHeads);
    return BlockAddedEvent.createForFork(fork);
  }

  private BlockAddedEvent handleChainReorg(
      final BlockchainStorage.Updater updater, final BlockWithReceipts newChainHeadWithReceipts) {
    final Block newChainHeadBlock = newChainHeadWithReceipts.getBlock();
    final Block oldChainHeadBlock = getChainHeadBlock();
    BlockHeader currentOldChainHeader = oldChainHeadBlock.getHeader();
    BlockHeader currentNewChainHeader = newChainHeadBlock.getHeader();

    // Update chain head
    updater.setChainHead(newChainHeadBlock.getHash());

    // Track transactions and logs to be added and removed
    final Map<Hash, List<Transaction>> newTransactions = new HashMap<>();
    final List<Transaction> removedTransactions = new ArrayList<>();
    final Deque<LogWithMetadata> logsWithMetadata = new ArrayDeque<>();

    while (currentNewChainHeader.getNumber() > currentOldChainHeader.getNumber()) {
      // If new chain is longer than old chain, walk back until we meet the old chain by number
      // adding indexing for new chain along the way.
      final Hash blockHash = currentNewChainHeader.getHash();
      updater.putBlockHash(currentNewChainHeader.getNumber(), blockHash);
      final List<Transaction> newTxs =
          blockchainStorage
              .getBlockBody(blockHash)
              .map(BlockBody::getTransactions)
              // If it's not in storage, it must be the new chain head
              .orElseGet(() -> newChainHeadBlock.getBody().getTransactions());
      newTransactions.put(blockHash, newTxs);

      addAddedLogsWithMetadata(
          logsWithMetadata,
          getBlockWithReceipts(currentNewChainHeader).orElse(newChainHeadWithReceipts));

      currentNewChainHeader =
          blockchainStorage.getBlockHeader(currentNewChainHeader.getParentHash()).get();
    }

    while (currentOldChainHeader.getNumber() > currentNewChainHeader.getNumber()) {
      // If oldChain is longer than new chain, walk back until we meet the new chain by number,
      // updating as we go.
      updater.removeBlockHash(currentOldChainHeader.getNumber());
      removedTransactions.addAll(
          blockchainStorage.getBlockBody(currentOldChainHeader.getHash()).get().getTransactions());

      addRemovedLogsWithMetadata(
          logsWithMetadata, getBlockWithReceipts(currentOldChainHeader).get());

      currentOldChainHeader =
          blockchainStorage.getBlockHeader(currentOldChainHeader.getParentHash()).get();
    }

    while (!currentOldChainHeader.getHash().equals(currentNewChainHeader.getHash())) {
      // Walk back until we meet the common ancestor between the two chains, updating as we go.
      final Hash newBlockHash = currentNewChainHeader.getHash();
      updater.putBlockHash(currentNewChainHeader.getNumber(), newBlockHash);

      // Collect transaction to be updated
      final List<Transaction> newTxs =
          blockchainStorage
              .getBlockBody(newBlockHash)
              .map(BlockBody::getTransactions)
              // If it's not in storage, it must be the new chain head
              .orElseGet(() -> newChainHeadBlock.getBody().getTransactions());

      newTransactions.put(newBlockHash, newTxs);
      removedTransactions.addAll(
          blockchainStorage.getBlockBody(currentOldChainHeader.getHash()).get().getTransactions());

      addAddedLogsWithMetadata(
          logsWithMetadata,
          getBlockWithReceipts(currentNewChainHeader).orElse(newChainHeadWithReceipts));
      addRemovedLogsWithMetadata(
          logsWithMetadata, getBlockWithReceipts(currentOldChainHeader).get());

      currentNewChainHeader =
          blockchainStorage.getBlockHeader(currentNewChainHeader.getParentHash()).get();
      currentOldChainHeader =
          blockchainStorage.getBlockHeader(currentOldChainHeader.getParentHash()).get();
    }

    // Update indexed transactions
    newTransactions.forEach(
        (blockHash, transactionsInBlock) -> {
          indexTransactionForBlock(updater, blockHash, transactionsInBlock);
          // Don't remove transactions that are being re-indexed.
          removedTransactions.removeAll(transactionsInBlock);
        });
    clearIndexedTransactionsForBlock(updater, removedTransactions);

    // Update tracked forks
    final Collection<Hash> forks = blockchainStorage.getForkHeads();
    // Old head is now a fork
    forks.add(oldChainHeadBlock.getHash());
    // Remove new chain head's parent if it was tracked as a fork
    final Optional<Hash> parentFork =
        forks.stream()
            .filter(f -> f.equals(newChainHeadWithReceipts.getHeader().getParentHash()))
            .findAny();
    parentFork.ifPresent(forks::remove);
    updater.setForkHeads(forks);
    return BlockAddedEvent.createForChainReorg(
        newChainHeadBlock,
        newTransactions.values().stream().flatMap(Collection::stream).collect(toList()),
        removedTransactions,
        new ArrayList<>(logsWithMetadata));
  }

  @Override
  public boolean rewindToBlock(final long blockNumber) {
    final Optional<Hash> blockHash = blockchainStorage.getBlockHash(blockNumber);
    if (blockHash.isEmpty()) {
      return false;
    }

    final BlockchainStorage.Updater updater = blockchainStorage.updater();
    try {
      final BlockHeader oldBlockHeader = blockchainStorage.getBlockHeader(blockHash.get()).get();
      final BlockWithReceipts blockWithReceipts = getBlockWithReceipts(oldBlockHeader).get();
      final Block block = blockWithReceipts.getBlock();

      handleChainReorg(updater, blockWithReceipts);
      updater.commit();

      updateCacheForNewCanonicalHead(block, calculateTotalDifficulty(block));
      return true;
    } catch (final NoSuchElementException e) {
      // Any Optional.get() calls in this block should be present, missing data means data
      // corruption or a bug.
      updater.rollback();
      throw new IllegalStateException("Blockchain is missing data that should be present.", e);
    }
  }

  void updateCacheForNewCanonicalHead(final Block block, final UInt256 uInt256) {
    chainHeader = block.getHeader();
    totalDifficulty = uInt256;
    chainHeadTransactionCount = block.getBody().getTransactions().size();
    chainHeadOmmerCount = block.getBody().getOmmers().size();
  }

  private static void indexTransactionForBlock(
      final BlockchainStorage.Updater updater, final Hash hash, final List<Transaction> txs) {
    for (int i = 0; i < txs.size(); i++) {
      final Hash txHash = txs.get(i).getHash();
      final TransactionLocation loc = new TransactionLocation(hash, i);
      updater.putTransactionLocation(txHash, loc);
    }
  }

  private static void clearIndexedTransactionsForBlock(
      final BlockchainStorage.Updater updater, final List<Transaction> txs) {
    for (final Transaction tx : txs) {
      updater.removeTransactionLocation(tx.getHash());
    }
  }

  @VisibleForTesting
  Set<Hash> getForks() {
    return new HashSet<>(blockchainStorage.getForkHeads());
  }

  private void setGenesis(final Block genesisBlock) {
    checkArgument(
        genesisBlock.getHeader().getNumber() == BlockHeader.GENESIS_BLOCK_NUMBER,
        "Invalid genesis block.");
    final Optional<Hash> maybeHead = blockchainStorage.getChainHead();
    if (!maybeHead.isPresent()) {
      // Initialize blockchain store with genesis block.
      final BlockchainStorage.Updater updater = blockchainStorage.updater();
      final Hash hash = genesisBlock.getHash();
      updater.putBlockHeader(hash, genesisBlock.getHeader());
      updater.putBlockBody(hash, genesisBlock.getBody());
      updater.putTransactionReceipts(hash, emptyList());
      updater.putTotalDifficulty(hash, calculateTotalDifficulty(genesisBlock));
      updater.putBlockHash(genesisBlock.getHeader().getNumber(), hash);
      updater.setChainHead(hash);
      updater.commit();
    } else {
      // Verify genesis block is consistent with stored blockchain.
      final Optional<Hash> genesisHash = getBlockHashByNumber(BlockHeader.GENESIS_BLOCK_NUMBER);
      if (!genesisHash.isPresent()) {
        throw new IllegalStateException("Blockchain is missing genesis block data.");
      }
      if (!genesisHash.get().equals(genesisBlock.getHash())) {
        throw new InvalidConfigurationException(
            "Supplied genesis block does not match stored chain data.\n"
                + "Please specify a different data directory with --data-path or specify the original genesis file with --genesis-file.");
      }
    }
  }

  private boolean blockIsAlreadyTracked(final Block block) {
    if (block.getHeader().getParentHash().equals(chainHeader.getHash())) {
      // If this block builds on our chain head it would have a higher TD and be the chain head
      // but since it isn't we mustn't have imported it yet.
      // Saves a db read for the most common case
      return false;
    }
    return blockchainStorage.getBlockHeader(block.getHash()).isPresent();
  }

  private boolean blockIsConnected(final Block block) {
    return blockchainStorage.getBlockHeader(block.getHeader().getParentHash()).isPresent();
  }

  private void addAddedLogsWithMetadata(
      final Deque<LogWithMetadata> logsWithMetadata, final BlockWithReceipts blockWithReceipts) {
    logsWithMetadata.addAll(blockWithReceipts.getLogsWithMetadata(false));
  }

  private void addRemovedLogsWithMetadata(
      final Deque<LogWithMetadata> logsWithMetadata, final BlockWithReceipts blockWithReceipts) {
    final var newLogsWithMetadata = blockWithReceipts.getLogsWithMetadata(true);
    for (int i = newLogsWithMetadata.size() - 1; i >= 0; i--) {
      logsWithMetadata.addFirst(newLogsWithMetadata.get(i));
    }
  }

  private Optional<BlockWithReceipts> getBlockWithReceipts(final BlockHeader blockHeader) {
    return blockchainStorage
        .getBlockBody(blockHeader.getHash())
        .map(body -> new Block(blockHeader, body))
        .flatMap(
            block ->
                blockchainStorage
                    .getTransactionReceipts(blockHeader.getHash())
                    .map(receipts -> new BlockWithReceipts(block, receipts)));
  }

  @Override
  public long observeBlockAdded(final BlockAddedObserver observer) {
    checkNotNull(observer);
    return blockAddedObservers.subscribe(observer);
  }

  @Override
  public boolean removeObserver(final long observerId) {
    return blockAddedObservers.unsubscribe(observerId);
  }

  @VisibleForTesting
  int observerCount() {
    return blockAddedObservers.getSubscriberCount();
  }

  private void notifyBlockAdded(final BlockAddedEvent event) {
    blockAddedObservers.forEach(observer -> observer.onBlockAdded(event, this));
  }
}
