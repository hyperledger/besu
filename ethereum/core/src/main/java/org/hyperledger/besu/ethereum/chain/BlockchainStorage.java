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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BlockchainStorage {

  Optional<Hash> getChainHead();

  Collection<Hash> getForkHeads();

  Optional<Hash> getFinalized();

  Optional<Hash> getSafeBlock();

  Optional<BlockHeader> getBlockHeader(Hash blockHash);

  Optional<BlockBody> getBlockBody(Hash blockHash);

  Optional<List<TransactionReceipt>> getTransactionReceipts(Hash blockHash);

  Optional<Hash> getBlockHash(long blockNumber);

  Optional<Difficulty> getTotalDifficulty(Hash blockHash);

  Optional<TransactionLocation> getTransactionLocation(Hash transactionHash);

  Updater updater();

  interface Updater {

    void putBlockHeader(Hash blockHash, BlockHeader blockHeader);

    void putBlockBody(Hash blockHash, BlockBody blockBody);

    void putTransactionLocation(Hash transactionHash, TransactionLocation transactionLocation);

    void putTransactionReceipts(Hash blockHash, List<TransactionReceipt> transactionReceipts);

    void putBlockHash(long blockNumber, Hash blockHash);

    void putTotalDifficulty(Hash blockHash, Difficulty totalDifficulty);

    void setChainHead(Hash blockHash);

    void setForkHeads(Collection<Hash> forkHeadHashes);

    void setFinalized(Hash blockHash);

    void setSafeBlock(Hash blockHash);

    void removeBlockHash(long blockNumber);

    void removeBlockHeader(final Hash blockHash);

    void removeBlockBody(final Hash blockHash);

    void removeTransactionReceipts(final Hash blockHash);

    void removeTransactionLocation(Hash transactionHash);

    void removeTotalDifficulty(final Hash blockHash);

    void commit();

    void rollback();
  }
}
