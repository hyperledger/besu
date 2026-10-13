/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.SyncBlockAccessList;
import org.hyperledger.besu.ethereum.core.SyncBlockBody;
import org.hyperledger.besu.ethereum.core.SyncTransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BlockchainStorage {

  Optional<Hash> getChainHead();

  Collection<Hash> getForkHeads();

  Optional<Hash> getFinalized();

  Optional<Hash> getSafeBlock();

  /**
   * The range of blocks whose bodies and receipts a snap sync left for later, if there is one.
   *
   * @return the blocks that have a header but no body and receipts yet
   */
  Optional<MissingBlockBodies> getMissingBlockBodies();

  Optional<BlockHeader> getBlockHeader(Hash blockHash);

  Optional<BlockBody> getBlockBody(Hash blockHash);

  Optional<BlockAccessList> getBlockAccessList(Hash blockHash);

  Optional<List<TransactionReceipt>> getTransactionReceipts(Hash blockHash);

  Optional<Hash> getBlockHash(long blockNumber);

  Optional<Difficulty> getTotalDifficulty(Hash blockHash);

  Optional<TransactionLocation> getTransactionLocation(Hash transactionHash);

  Optional<Hash> getTransactionHashBySenderAndNonce(Address sender, long nonce);

  Updater updater();

  /**
   * An updater whose writes to the block data skip the write-ahead log of the storage. A crash
   * loses them until {@link #flush()} returns. For blocks that can be downloaded again.
   *
   * @return the updater
   */
  default Updater unloggedUpdater() {
    return updater();
  }

  /** Writes the block data that is in memory to disk, and returns when it is there. */
  default void flush() {}

  /**
   * Whether blocks that were stored are still waiting in memory to be written out. Storing more
   * while they do can make the storage stop every write until they are.
   *
   * @return true if a write buffer of the storage waits to be flushed
   */
  default boolean isWriteBufferWaitingForFlush() {
    return false;
  }

  interface Updater {

    void putBlockHeader(Hash blockHash, BlockHeader blockHeader);

    void putBlockBody(Hash blockHash, BlockBody blockBody);

    void putSyncBlockBody(Hash blockHash, SyncBlockBody blockBody);

    void putBlockAccessList(Hash blockHash, BlockAccessList blockAccessList);

    void putSyncBlockAccessList(Hash blockHash, SyncBlockAccessList syncBlockAccessList);

    void putTransactionLocation(Hash transactionHash, TransactionLocation transactionLocation);

    /**
     * Puts the supplied TransactionReceipts in the database against the supplied block Hash
     *
     * @param blockHash The block hash to store the receipts against
     * @param transactionReceipts The receipts to be stored
     */
    void putTransactionReceipts(Hash blockHash, List<TransactionReceipt> transactionReceipts);

    /**
     * Puts the supplied SyncTransactionReceipts' rawRlp in the database against the supplied block
     * Hash. This is done for performance reasons during sync.
     *
     * @param blockHash The block hash to store the receipts against
     * @param transactionReceipts The receipts to be stored
     */
    void putSyncTransactionReceipts(
        Hash blockHash, List<SyncTransactionReceipt> transactionReceipts);

    void putBlockHash(long blockNumber, Hash blockHash);

    void putTotalDifficulty(Hash blockHash, Difficulty totalDifficulty);

    void setChainHead(Hash blockHash);

    void setForkHeads(Collection<Hash> forkHeadHashes);

    void setFinalized(Hash blockHash);

    void setSafeBlock(Hash blockHash);

    void setMissingBlockBodies(MissingBlockBodies missingBlockBodies);

    void removeMissingBlockBodies();

    void removeBlockHash(long blockNumber);

    void removeBlockHeader(final Hash blockHash);

    void removeBlockBody(final Hash blockHash);

    void removeBlockAccessList(final Hash blockHash);

    void removeTransactionReceipts(final Hash blockHash);

    void removeTransactionLocation(Hash transactionHash);

    void putTransactionHashBySenderAndNonce(Address sender, long nonce, Hash transactionHash);

    void removeTransactionHashBySenderAndNonce(Address sender, long nonce);

    void removeTotalDifficulty(final Hash blockHash);

    void commit();

    void rollback();
  }
}
