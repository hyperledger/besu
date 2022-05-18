/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class KeyValueStoragePrefixedKeyBlockchainStorage implements BlockchainStorage {

  private static final Bytes CHAIN_HEAD_KEY =
      Bytes.wrap("chainHeadHash".getBytes(StandardCharsets.UTF_8));
  private static final Bytes FORK_HEADS_KEY =
      Bytes.wrap("forkHeads".getBytes(StandardCharsets.UTF_8));
  private static final Bytes FINALIZED_BLOCK_HASH_KEY =
      Bytes.wrap("finalizedBlockHash".getBytes(StandardCharsets.UTF_8));
  private static final Bytes SAFE_BLOCK_HASH_KEY =
      Bytes.wrap("safeBlockHash".getBytes(StandardCharsets.UTF_8));

  private static final Bytes VARIABLES_PREFIX = Bytes.of(1);
  static final Bytes BLOCK_HEADER_PREFIX = Bytes.of(2);
  private static final Bytes BLOCK_BODY_PREFIX = Bytes.of(3);
  private static final Bytes TRANSACTION_RECEIPTS_PREFIX = Bytes.of(4);
  private static final Bytes BLOCK_HASH_PREFIX = Bytes.of(5);
  private static final Bytes TOTAL_DIFFICULTY_PREFIX = Bytes.of(6);
  private static final Bytes TRANSACTION_LOCATION_PREFIX = Bytes.of(7);

  final KeyValueStorage storage;
  final BlockHeaderFunctions blockHeaderFunctions;

  public KeyValueStoragePrefixedKeyBlockchainStorage(
      final KeyValueStorage storage, final BlockHeaderFunctions blockHeaderFunctions) {
    this.storage = storage;
    this.blockHeaderFunctions = blockHeaderFunctions;
  }

  @Override
  public Optional<Hash> getChainHead() {
    return get(VARIABLES_PREFIX, CHAIN_HEAD_KEY).map(this::bytesToHash);
  }

  @Override
  public Collection<Hash> getForkHeads() {
    return get(VARIABLES_PREFIX, FORK_HEADS_KEY)
        .map(bytes -> RLP.input(bytes).readList(in -> this.bytesToHash(in.readBytes32())))
        .orElse(Lists.newArrayList());
  }

  @Override
  public Optional<Hash> getFinalized() {
    return get(VARIABLES_PREFIX, FINALIZED_BLOCK_HASH_KEY).map(this::bytesToHash);
  }

  @Override
  public Optional<Hash> getSafeBlock() {
    return get(VARIABLES_PREFIX, SAFE_BLOCK_HASH_KEY).map(this::bytesToHash);
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final Hash blockHash) {
    return get(BLOCK_HEADER_PREFIX, blockHash)
        .map(b -> BlockHeader.readFrom(RLP.input(b), blockHeaderFunctions));
  }

  @Override
  public Optional<BlockBody> getBlockBody(final Hash blockHash) {
    return get(BLOCK_BODY_PREFIX, blockHash)
        .map(bytes -> BlockBody.readFrom(RLP.input(bytes), blockHeaderFunctions));
  }

  @Override
  public Optional<List<TransactionReceipt>> getTransactionReceipts(final Hash blockHash) {
    return get(TRANSACTION_RECEIPTS_PREFIX, blockHash).map(this::rlpDecodeTransactionReceipts);
  }

  @Override
  public Optional<Hash> getBlockHash(final long blockNumber) {
    return get(BLOCK_HASH_PREFIX, UInt256.valueOf(blockNumber)).map(this::bytesToHash);
  }

  @Override
  public Optional<Difficulty> getTotalDifficulty(final Hash blockHash) {
    return get(TOTAL_DIFFICULTY_PREFIX, blockHash).map(b -> Difficulty.wrap(Bytes32.wrap(b, 0)));
  }

  @Override
  public Optional<TransactionLocation> getTransactionLocation(final Hash transactionHash) {
    return get(TRANSACTION_LOCATION_PREFIX, transactionHash)
        .map(bytes -> TransactionLocation.readFrom(RLP.input(bytes)));
  }

  @Override
  public Updater updater() {
    return new Updater(storage.startTransaction());
  }

  private List<TransactionReceipt> rlpDecodeTransactionReceipts(final Bytes bytes) {
    return RLP.input(bytes).readList(TransactionReceipt::readFrom);
  }

  private Hash bytesToHash(final Bytes bytes) {
    return Hash.wrap(Bytes32.wrap(bytes, 0));
  }

  Optional<Bytes> get(final Bytes prefix, final Bytes key) {
    return storage.get(Bytes.concatenate(prefix, key).toArrayUnsafe()).map(Bytes::wrap);
  }

  public static class Updater implements BlockchainStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public void putBlockHeader(final Hash blockHash, final BlockHeader blockHeader) {
      set(BLOCK_HEADER_PREFIX, blockHash, RLP.encode(blockHeader::writeTo));
    }

    @Override
    public void putBlockBody(final Hash blockHash, final BlockBody blockBody) {
      set(BLOCK_BODY_PREFIX, blockHash, RLP.encode(blockBody::writeTo));
    }

    @Override
    public void putTransactionLocation(
        final Hash transactionHash, final TransactionLocation transactionLocation) {
      set(TRANSACTION_LOCATION_PREFIX, transactionHash, RLP.encode(transactionLocation::writeTo));
    }

    @Override
    public void putTransactionReceipts(
        final Hash blockHash, final List<TransactionReceipt> transactionReceipts) {
      set(TRANSACTION_RECEIPTS_PREFIX, blockHash, rlpEncode(transactionReceipts));
    }

    @Override
    public void putBlockHash(final long blockNumber, final Hash blockHash) {
      set(BLOCK_HASH_PREFIX, UInt256.valueOf(blockNumber), blockHash);
    }

    @Override
    public void putTotalDifficulty(final Hash blockHash, final Difficulty totalDifficulty) {
      set(TOTAL_DIFFICULTY_PREFIX, blockHash, totalDifficulty);
    }

    @Override
    public void setChainHead(final Hash blockHash) {
      set(VARIABLES_PREFIX, CHAIN_HEAD_KEY, blockHash);
    }

    @Override
    public void setForkHeads(final Collection<Hash> forkHeadHashes) {
      final Bytes data =
          RLP.encode(o -> o.writeList(forkHeadHashes, (val, out) -> out.writeBytes(val)));
      set(VARIABLES_PREFIX, FORK_HEADS_KEY, data);
    }

    @Override
    public void setFinalized(final Hash blockHash) {
      set(VARIABLES_PREFIX, FINALIZED_BLOCK_HASH_KEY, blockHash);
    }

    @Override
    public void setSafeBlock(final Hash blockHash) {
      set(VARIABLES_PREFIX, SAFE_BLOCK_HASH_KEY, blockHash);
    }

    @Override
    public void removeBlockHash(final long blockNumber) {
      remove(BLOCK_HASH_PREFIX, UInt256.valueOf(blockNumber));
    }

    @Override
    public void removeTransactionLocation(final Hash transactionHash) {
      remove(TRANSACTION_LOCATION_PREFIX, transactionHash);
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }

    void set(final Bytes prefix, final Bytes key, final Bytes value) {
      transaction.put(Bytes.concatenate(prefix, key).toArrayUnsafe(), value.toArrayUnsafe());
    }

    private void remove(final Bytes prefix, final Bytes key) {
      transaction.remove(Bytes.concatenate(prefix, key).toArrayUnsafe());
    }

    private Bytes rlpEncode(final List<TransactionReceipt> receipts) {
      return RLP.encode(o -> o.writeList(receipts, TransactionReceipt::writeToWithRevertReason));
    }
  }
}
