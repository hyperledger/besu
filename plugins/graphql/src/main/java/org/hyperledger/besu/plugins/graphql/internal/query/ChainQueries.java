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
package org.hyperledger.besu.plugins.graphql.internal.query;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.Log;
import org.hyperledger.besu.plugin.data.LogWithMetadata;
import org.hyperledger.besu.plugin.data.TransactionLocation;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.WorldStateService;
import org.hyperledger.besu.plugins.graphql.GraphQLContextType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import graphql.GraphQLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chain queries used by the GraphQL data fetchers, built on the plugin services. Each query has the
 * same semantics as the query of the same name in Besu's {@code BlockchainQueries}, which served
 * GraphQL before it became a plugin.
 */
public class ChainQueries {
  private static final Logger LOG = LoggerFactory.getLogger(ChainQueries.class);

  private final BlockchainService blockchainService;
  private final WorldStateService worldStateService;
  private final boolean bonsaiWorldState;

  /**
   * Creates the chain queries.
   *
   * @param blockchainService the blockchain service
   * @param worldStateService the world state service
   * @param bonsaiWorldState whether the node stores its world state in a Bonsai format
   */
  public ChainQueries(
      final BlockchainService blockchainService,
      final WorldStateService worldStateService,
      final boolean bonsaiWorldState) {
    this.blockchainService = blockchainService;
    this.worldStateService = worldStateService;
    this.bonsaiWorldState = bonsaiWorldState;
  }

  /**
   * Creates the chain queries from the services in a GraphQL context.
   *
   * @param context the GraphQL context
   * @return the chain queries
   */
  public static ChainQueries fromContext(final GraphQLContext context) {
    final Boolean bonsai = context.get(GraphQLContextType.BONSAI_WORLD_STATE);
    return new ChainQueries(
        context.get(GraphQLContextType.BLOCKCHAIN_SERVICE),
        context.get(GraphQLContextType.WORLD_STATE_SERVICE),
        Boolean.TRUE.equals(bonsai));
  }

  /**
   * Returns the blockchain service.
   *
   * @return the blockchain service
   */
  public BlockchainService getBlockchainService() {
    return blockchainService;
  }

  /**
   * Whether the node stores its world state in a Bonsai format.
   *
   * @return true for a Bonsai world state
   */
  public boolean isBonsaiWorldState() {
    return bonsaiWorldState;
  }

  /**
   * Returns the number of the chain head.
   *
   * @return the chain head block number
   */
  public long headBlockNumber() {
    return blockchainService.getChainHeadHeader().getNumber();
  }

  /**
   * Returns the block with the given hash. A block whose total difficulty is not stored is not
   * found.
   *
   * @param blockHash the block hash
   * @return the block
   */
  public Optional<BlockWithMetadata> blockByHash(final Hash blockHash) {
    return blockchainService.getBlockByHash(blockHash).flatMap(this::withTotalDifficulty);
  }

  /**
   * Returns the canonical block with the given number. A block whose total difficulty is not stored
   * is not found.
   *
   * @param number the block number
   * @return the block
   */
  public Optional<BlockWithMetadata> blockByNumber(final long number) {
    return blockchainService.getBlockByNumber(number).flatMap(this::withTotalDifficulty);
  }

  /**
   * Returns the chain head block.
   *
   * @return the chain head block
   */
  public Optional<BlockWithMetadata> latestBlock() {
    return blockByHash(blockchainService.getChainHeadHash());
  }

  private Optional<BlockWithMetadata> withTotalDifficulty(final BlockContext blockContext) {
    final BlockHeader header = blockContext.getBlockHeader();
    return blockchainService
        .getTotalDifficulty(header.getBlockHash())
        .map(td -> new BlockWithMetadata(header, blockContext.getBlockBody(), td));
  }

  /**
   * Returns the canonical block header with the given number.
   *
   * @param number the block number
   * @return the block header
   */
  public Optional<BlockHeader> getBlockHeaderByNumber(final long number) {
    return blockchainService.getBlockHeaderByNumber(number);
  }

  /**
   * Returns the body of the block with the given hash.
   *
   * @param blockHash the block hash
   * @return the block body
   */
  public Optional<BlockBody> getBlockBody(final Hash blockHash) {
    return blockchainService.getBlockByHash(blockHash).map(BlockContext::getBlockBody);
  }

  /**
   * Returns the ommer at the given index of the block with the given hash.
   *
   * @param blockHash the block hash
   * @param index the index of the ommer
   * @return the ommer header, empty if the block is unknown or has no ommer at that index
   */
  public Optional<BlockHeader> getOmmer(final Hash blockHash, final int index) {
    return getBlockBody(blockHash)
        .map(
            body -> {
              final List<? extends BlockHeader> ommers = body.getOmmers();
              return ommers.size() > index ? ommers.get(index) : null;
            });
  }

  /**
   * Returns the transaction with the given hash, if it is included in a canonical block.
   *
   * @param transactionHash the transaction hash
   * @return the transaction with its location
   */
  public Optional<TransactionWithMetadata> transactionByHash(final Hash transactionHash) {
    final Optional<TransactionLocation> maybeLocation =
        blockchainService.getTransactionLocation(transactionHash);
    if (maybeLocation.isEmpty()) {
      return Optional.empty();
    }
    final TransactionLocation loc = maybeLocation.get();
    final Hash blockHash = loc.getBlockHash();
    // the location is only known for a stored transaction and block, so a missing one is a bug
    final BlockHeader header = blockchainService.getBlockHeaderByHash(blockHash).orElseThrow();
    final Transaction transaction =
        blockchainService.getTransactionByHash(transactionHash).orElseThrow();
    return Optional.of(
        new TransactionWithMetadata(
            transaction,
            header.getNumber(),
            Quantities.toWei(header.getBaseFee()),
            blockHash,
            loc.getTransactionIndex(),
            header.getTimestamp()));
  }

  /**
   * Returns the receipts of the transactions of the block with the given hash.
   *
   * @param blockHash the block hash
   * @return the receipts, empty if the block is unknown
   */
  public Optional<List<TransactionReceiptWithMetadata>> transactionReceiptsByBlockHash(
      final Hash blockHash) {
    final Optional<BlockContext> block = blockchainService.getBlockByHash(blockHash);
    if (block.isEmpty()) {
      return Optional.empty();
    }
    final BlockHeader header = block.get().getBlockHeader();
    final List<? extends Transaction> transactions = block.get().getBlockBody().getTransactions();

    final List<TransactionReceipt> transactionReceipts =
        blockchainService.getReceiptsByBlockHash(blockHash).orElseThrow();

    long cumulativeGasUsedUntilTx = 0;
    int logIndexOffset = 0;

    final List<TransactionReceiptWithMetadata> receiptsResult =
        new ArrayList<>(transactions.size());

    for (int transactionIndex = 0; transactionIndex < transactions.size(); transactionIndex++) {
      final Transaction transaction = transactions.get(transactionIndex);
      final TransactionReceipt transactionReceipt = transactionReceipts.get(transactionIndex);
      final Hash transactionHash = transaction.getHash();

      final long gasUsed = transactionReceipt.getCumulativeGasUsed() - cumulativeGasUsedUntilTx;

      receiptsResult.add(
          new TransactionReceiptWithMetadata(
              transactionReceipt,
              transaction,
              transactionHash,
              transactionIndex,
              gasUsed,
              Quantities.toWei(header.getBaseFee()),
              blockHash,
              header.getTimestamp(),
              header.getNumber(),
              getBlobGasUsed(transaction, header),
              getBlobGasPrice(transaction, header),
              logIndexOffset));

      cumulativeGasUsedUntilTx = transactionReceipt.getCumulativeGasUsed();
      logIndexOffset += transactionReceipt.getLogs().size();
    }
    return Optional.of(receiptsResult);
  }

  /**
   * Returns the receipt of the transaction with the given hash, if it is included in a canonical
   * block.
   *
   * @param transactionHash the transaction hash
   * @return the receipt
   */
  public Optional<TransactionReceiptWithMetadata> transactionReceiptByTransactionHash(
      final Hash transactionHash) {
    final Optional<TransactionLocation> maybeLocation =
        blockchainService.getTransactionLocation(transactionHash);
    if (maybeLocation.isEmpty()) {
      return Optional.empty();
    }
    // the location is only known for a stored transaction and block, so a missing one is a bug
    final TransactionLocation location = maybeLocation.get();
    final Hash blockhash = location.getBlockHash();
    final int transactionIndex = location.getTransactionIndex();

    final BlockContext block = blockchainService.getBlockByHash(blockhash).orElseThrow();
    final Transaction transaction = block.getBlockBody().getTransactions().get(transactionIndex);

    final BlockHeader header = block.getBlockHeader();
    final List<TransactionReceipt> transactionReceipts =
        blockchainService.getReceiptsByBlockHash(blockhash).orElseThrow();
    final TransactionReceipt transactionReceipt = transactionReceipts.get(transactionIndex);

    long gasUsed = transactionReceipt.getCumulativeGasUsed();
    int logIndexOffset = 0;
    if (transactionIndex > 0) {
      gasUsed -= transactionReceipts.get(transactionIndex - 1).getCumulativeGasUsed();
      for (int i = 0; i < transactionIndex; i++) {
        logIndexOffset += transactionReceipts.get(i).getLogs().size();
      }
    }

    return Optional.of(
        new TransactionReceiptWithMetadata(
            transactionReceipt,
            transaction,
            transactionHash,
            transactionIndex,
            gasUsed,
            Quantities.toWei(header.getBaseFee()),
            blockhash,
            header.getTimestamp(),
            header.getNumber(),
            getBlobGasUsed(transaction, header),
            getBlobGasPrice(transaction, header),
            logIndexOffset));
  }

  private Optional<Long> getBlobGasUsed(final Transaction transaction, final BlockHeader header) {
    return transaction.getType().supportsBlob()
        ? Optional.of(blockchainService.getBlobGasCost(header, blobCount(transaction)))
        : Optional.empty();
  }

  private static int blobCount(final Transaction transaction) {
    return transaction.getVersionedHashes().map(List::size).orElse(0);
  }

  // The price is computed from the parent's excess blob gas, so it is unknown without the parent.
  private Optional<Wei> getBlobGasPrice(final Transaction transaction, final BlockHeader header) {
    if (transaction.getType().supportsBlob()) {
      return blockchainService
          .getBlockHeaderByHash(header.getParentHash())
          .map(parentHeader -> blockchainService.getBlobGasPrice(header));
    }
    return Optional.empty();
  }

  /**
   * Returns the logs of a transaction in the block with the given hash, with block-relative
   * indexes.
   *
   * @param blockHash the block hash
   * @param transactionWithMetadata the transaction
   * @return the logs of the transaction, empty if the block is unknown
   */
  public List<LogWithMetadata> matchingLogs(
      final Hash blockHash, final TransactionWithMetadata transactionWithMetadata) {
    if (transactionWithMetadata.getTransactionIndex().isEmpty()) {
      throw new RuntimeException(
          "Cannot find logs because transaction "
              + transactionWithMetadata.getTransaction().getHash()
              + " does not have a transaction index");
    }

    try {
      final Optional<BlockHeader> blockHeader = blockchainService.getBlockHeaderByHash(blockHash);
      if (blockHeader.isEmpty()) {
        return new ArrayList<>();
      }
      // receipts and transactions should exist if the header exists, so throwing is ok.
      final List<TransactionReceipt> receipts =
          blockchainService.getReceiptsByBlockHash(blockHash).orElse(Collections.emptyList());
      final List<? extends Transaction> transactions =
          getBlockBody(blockHash)
              .<List<? extends Transaction>>map(BlockBody::getTransactions)
              .orElse(Collections.emptyList());
      final long number = blockHeader.get().getNumber();

      final int transactionIndex = transactionWithMetadata.getTransactionIndex().get();
      final int logIndexOffset =
          logIndexOffset(
              transactionWithMetadata.getTransaction().getHash(), receipts, transactions);

      final List<? extends Log> receiptLogs = receipts.get(transactionIndex).getLogs();
      final Hash transactionHash = transactions.get(transactionIndex).getHash();
      final List<LogWithMetadata> logs = new ArrayList<>();
      for (int logIndex = 0; logIndex < receiptLogs.size(); ++logIndex) {
        final Log log = receiptLogs.get(logIndex);
        logs.add(
            new TransactionLog(
                logIndexOffset + logIndex,
                number,
                blockHash,
                transactionHash,
                transactionIndex,
                log.getLogger(),
                log.getData(),
                log.getTopics()));
      }
      return logs;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static int logIndexOffset(
      final Hash transactionHash,
      final List<TransactionReceipt> receipts,
      final List<? extends Transaction> transactions) {
    int logIndexOffset = 0;
    for (int i = 0; i < receipts.size(); i++) {
      if (transactions.get(i).getHash().equals(transactionHash)) {
        break;
      }

      logIndexOffset += receipts.get(i).getLogs().size();
    }

    return logIndexOffset;
  }

  /**
   * Applies the mapper to the world state after the canonical block with the given number.
   *
   * @param blockNumber the block number
   * @param mapper the function to apply to the world state, which must not let the world state or
   *     its accounts escape
   * @param <U> the type of the mapped value
   * @return the mapped value, empty if the block or its world state is unavailable
   */
  public <U> Optional<U> getAndMapWorldState(
      final long blockNumber, final Function<? super WorldView, ? extends Optional<U>> mapper) {
    return getBlockHeaderByNumber(blockNumber)
        .flatMap(header -> getAndMapWorldState(header.getBlockHash(), mapper));
  }

  /**
   * Applies the mapper to the world state after the canonical block with the given number, passing
   * the mapper the hash of the block as well.
   *
   * @param blockNumber the block number
   * @param mapper the function to apply to the world state and block hash, which must not let the
   *     world state or its accounts escape
   * @param <U> the type of the mapped value
   * @return the mapped value, empty if the block or its world state is unavailable
   */
  public <U> Optional<U> getAndMapWorldStateWithHash(
      final long blockNumber,
      final BiFunction<? super WorldView, Hash, ? extends Optional<U>> mapper) {
    return getBlockHeaderByNumber(blockNumber)
        .map(BlockHeader::getBlockHash)
        .flatMap(hash -> getAndMapWorldState(hash, ws -> mapper.apply(ws, hash)));
  }

  /**
   * Applies the mapper to the world state after the block with the given hash.
   *
   * @param blockHash the block hash
   * @param mapper the function to apply to the world state, which must not let the world state or
   *     its accounts escape
   * @param <U> the type of the mapped value
   * @return the mapped value, empty if the block or its world state is unavailable, or if the
   *     mapper fails
   */
  public <U> Optional<U> getAndMapWorldState(
      final Hash blockHash, final Function<? super WorldView, ? extends Optional<U>> mapper) {
    // A failing mapper, for example one that reads state missing from storage, gives an empty
    // value, as it did when GraphQL was served by Besu's core.
    try {
      return worldStateService.mapWorldView(blockHash, mapper);
    } catch (final RuntimeException ex) {
      LOG.error("failed worldstate query for " + blockHash.getBytes().toShortHexString(), ex);
      return Optional.empty();
    }
  }
}
