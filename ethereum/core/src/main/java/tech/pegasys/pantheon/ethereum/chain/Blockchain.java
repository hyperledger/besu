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
package tech.pegasys.pantheon.ethereum.chain;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.List;
import java.util.Optional;

/** An interface for reading data from the blockchain. */
public interface Blockchain {
  /**
   * Return the head (latest block hash and total difficulty) of the local canonical blockchain.
   *
   * @return The head of the blockchain.
   */
  ChainHead getChainHead();

  /**
   * Return the block number of the head of the canonical chain.
   *
   * @return The block number of the head of the chain.
   */
  long getChainHeadBlockNumber();

  /**
   * Return the hash of the head of the canonical chain.
   *
   * @return The hash of the head of the chain.
   */
  Hash getChainHeadHash();

  /** @return the header for the current chain head */
  default BlockHeader getChainHeadHeader() {
    return getBlockHeader(getChainHeadHash())
        .orElseThrow(() -> new IllegalStateException("Missing chain head header."));
  }

  default Block getChainHeadBlock() {
    final Hash chainHeadHash = getChainHeadHash();
    final BlockHeader header =
        getBlockHeader(chainHeadHash)
            .orElseThrow(() -> new IllegalStateException("Missing chain head header."));
    final BlockBody body =
        getBlockBody(chainHeadHash)
            .orElseThrow(() -> new IllegalStateException("Missing chain head body."));
    return new Block(header, body);
  }

  default Block getGenesisBlock() {
    final Hash genesisHash =
        getBlockHashByNumber(BlockHeader.GENESIS_BLOCK_NUMBER)
            .orElseThrow(() -> new IllegalStateException("Missing genesis block."));
    return getBlockByHash(genesisHash);
  }

  default Block getBlockByHash(final Hash blockHash) {
    final BlockHeader header =
        getBlockHeader(blockHash).orElseThrow(() -> new IllegalStateException("Missing block."));
    final BlockBody body =
        getBlockBody(blockHash).orElseThrow(() -> new IllegalStateException("Missing block."));
    return new Block(header, body);
  }

  /**
   * Checks whether the block corresponding to the given hash is on the canonical chain.
   *
   * @param blockHeaderHash The hash of the block to check.
   * @return true if the block corresponding to the hash is on the canonical chain.
   */
  default boolean blockIsOnCanonicalChain(final Hash blockHeaderHash) {
    return getBlockHeader(blockHeaderHash)
        .flatMap(h -> getBlockHashByNumber(h.getNumber()))
        .filter(h -> h.equals(blockHeaderHash))
        .isPresent();
  }

  /**
   * Returns the block header corresponding to the given block number on the canonical chain.
   *
   * @param blockNumber The reference block number whose header we want to retrieve.
   * @return The block header corresponding to this block number.
   */
  Optional<BlockHeader> getBlockHeader(long blockNumber);

  /**
   * Return true if the block corresponding the hash is present.
   *
   * @param blockHash The hash of the block to check.
   * @return true if the block is tracked.
   */
  default boolean contains(final Hash blockHash) {
    return getBlockHeader(blockHash).isPresent();
  }

  /**
   * Returns the block header corresponding to the given block hash. Associated block is not
   * necessarily on the canonical chain.
   *
   * @param blockHeaderHash The hash of the block whose header we want to retrieve.
   * @return The block header corresponding to this block hash.
   */
  Optional<BlockHeader> getBlockHeader(Hash blockHeaderHash);

  /**
   * Returns the block body corresponding to the given block header hash. Associated block is not
   * necessarily on the canonical chain.
   *
   * @param blockHeaderHash The block header hash identifying the block whose body should be
   *     returned.
   * @return The block body corresponding to the target block.
   */
  Optional<BlockBody> getBlockBody(Hash blockHeaderHash);

  /**
   * Given a block's hash, returns the list of transaction receipts associated with this block's
   * transactions. Associated block is not necessarily on the canonical chain.
   *
   * @param blockHeaderHash The header hash of the block we're querying.
   * @return The transaction receipts corresponding to block hash.
   */
  Optional<List<TransactionReceipt>> getTxReceipts(Hash blockHeaderHash);

  /**
   * Retrieves the header hash of the block at the given height in the canonical chain.
   *
   * @param number The height of the block whose hash should be retrieved.
   * @return The hash of the block at the given height.
   */
  Optional<Hash> getBlockHashByNumber(long number);

  /**
   * Returns the total difficulty (cumulative difficulty up to and including the target block) of
   * the block corresponding to the given hash. Associated block is not necessarily on the canonical
   * chain.
   *
   * @param blockHeaderHash The hash of the block header being queried.
   * @return The total difficulty of the corresponding block.
   */
  Optional<UInt256> getTotalDifficultyByHash(Hash blockHeaderHash);

  /**
   * Given a transaction hash, returns the location (block number and transaction index) of the
   * hashed transaction on the canonical chain.
   *
   * @param transactionHash A transaction hash.
   * @return The location of the hashed transaction.
   */
  Optional<Transaction> getTransactionByHash(Hash transactionHash);

  /**
   * @param transactionHash A transaction hash.
   * @return The transaction location associated with the corresponding hash.
   */
  Optional<TransactionLocation> getTransactionLocation(Hash transactionHash);

  /**
   * Adds an observer that will get called when a new block is added.
   *
   * <p><i>No guarantees are made about the order in which observers are invoked.</i>
   *
   * @param observer the observer to call
   * @return the observer ID that can be used to remove it later.
   */
  long observeBlockAdded(BlockAddedObserver observer);

  /**
   * Removes an previously added observer of any type.
   *
   * @param observerId the ID of the observer to remove
   * @return {@code true} if the observer was removed; otherwise {@code false}
   */
  boolean removeObserver(long observerId);
}
