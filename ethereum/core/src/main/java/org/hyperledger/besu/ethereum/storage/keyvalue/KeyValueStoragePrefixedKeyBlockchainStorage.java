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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.TransactionLocation;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;
import org.hyperledger.besu.util.uint.UInt256Bytes;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

public class KeyValueStoragePrefixedKeyBlockchainStorage implements BlockchainStorage {

  private static final BytesValue CHAIN_HEAD_KEY =
      BytesValue.wrap("chainHeadHash".getBytes(StandardCharsets.UTF_8));
  private static final BytesValue FORK_HEADS_KEY =
      BytesValue.wrap("forkHeads".getBytes(StandardCharsets.UTF_8));

  private static final BytesValue CONSTANTS_PREFIX = BytesValue.of(1);
  private static final BytesValue BLOCK_HEADER_PREFIX = BytesValue.of(2);
  private static final BytesValue BLOCK_BODY_PREFIX = BytesValue.of(3);
  private static final BytesValue TRANSACTION_RECEIPTS_PREFIX = BytesValue.of(4);
  private static final BytesValue BLOCK_HASH_PREFIX = BytesValue.of(5);
  private static final BytesValue TOTAL_DIFFICULTY_PREFIX = BytesValue.of(6);
  private static final BytesValue TRANSACTION_LOCATION_PREFIX = BytesValue.of(7);

  private final KeyValueStorage storage;
  private final BlockHeaderFunctions blockHeaderFunctions;

  public KeyValueStoragePrefixedKeyBlockchainStorage(
      final KeyValueStorage storage, final BlockHeaderFunctions blockHeaderFunctions) {
    this.storage = storage;
    this.blockHeaderFunctions = blockHeaderFunctions;
  }

  @Override
  public Optional<Hash> getChainHead() {
    return get(CONSTANTS_PREFIX, CHAIN_HEAD_KEY).map(this::bytesToHash);
  }

  @Override
  public Collection<Hash> getForkHeads() {
    return get(CONSTANTS_PREFIX, FORK_HEADS_KEY)
        .map(bytes -> RLP.input(bytes).readList(in -> this.bytesToHash(in.readBytes32())))
        .orElse(Lists.newArrayList());
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final Hash blockHash) {
    return get(BLOCK_HEADER_PREFIX, blockHash)
        .map(b -> BlockHeader.readFrom(RLP.input(b), blockHeaderFunctions));
  }

  @Override
  public Optional<BlockBody> getBlockBody(final Hash blockHash) {
    return get(BLOCK_BODY_PREFIX, blockHash)
        .map(bytesValue -> BlockBody.readFrom(RLP.input(bytesValue), blockHeaderFunctions));
  }

  @Override
  public Optional<List<TransactionReceipt>> getTransactionReceipts(final Hash blockHash) {
    return get(TRANSACTION_RECEIPTS_PREFIX, blockHash).map(this::rlpDecodeTransactionReceipts);
  }

  @Override
  public Optional<Hash> getBlockHash(final long blockNumber) {
    return get(BLOCK_HASH_PREFIX, UInt256Bytes.of(blockNumber)).map(this::bytesToHash);
  }

  @Override
  public Optional<UInt256> getTotalDifficulty(final Hash blockHash) {
    return get(TOTAL_DIFFICULTY_PREFIX, blockHash).map(b -> UInt256.wrap(Bytes32.wrap(b, 0)));
  }

  @Override
  public Optional<TransactionLocation> getTransactionLocation(final Hash transactionHash) {
    return get(TRANSACTION_LOCATION_PREFIX, transactionHash)
        .map(bytesValue -> TransactionLocation.readFrom(RLP.input(bytesValue)));
  }

  @Override
  public Updater updater() {
    return new Updater(storage.startTransaction());
  }

  private List<TransactionReceipt> rlpDecodeTransactionReceipts(final BytesValue bytes) {
    return RLP.input(bytes).readList(TransactionReceipt::readFrom);
  }

  private Hash bytesToHash(final BytesValue bytesValue) {
    return Hash.wrap(Bytes32.wrap(bytesValue, 0));
  }

  private Optional<BytesValue> get(final BytesValue prefix, final BytesValue key) {
    return storage.get(BytesValues.concatenate(prefix, key).getArrayUnsafe()).map(BytesValue::wrap);
  }

  public static class Updater implements BlockchainStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
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
      set(BLOCK_HASH_PREFIX, UInt256Bytes.of(blockNumber), blockHash);
    }

    @Override
    public void putTotalDifficulty(final Hash blockHash, final UInt256 totalDifficulty) {
      set(TOTAL_DIFFICULTY_PREFIX, blockHash, totalDifficulty.getBytes());
    }

    @Override
    public void setChainHead(final Hash blockHash) {
      set(CONSTANTS_PREFIX, CHAIN_HEAD_KEY, blockHash);
    }

    @Override
    public void setForkHeads(final Collection<Hash> forkHeadHashes) {
      final BytesValue data =
          RLP.encode(o -> o.writeList(forkHeadHashes, (val, out) -> out.writeBytesValue(val)));
      set(CONSTANTS_PREFIX, FORK_HEADS_KEY, data);
    }

    @Override
    public void removeBlockHash(final long blockNumber) {
      remove(BLOCK_HASH_PREFIX, UInt256Bytes.of(blockNumber));
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

    private void set(final BytesValue prefix, final BytesValue key, final BytesValue value) {
      transaction.put(
          BytesValues.concatenate(prefix, key).getArrayUnsafe(), value.getArrayUnsafe());
    }

    private void remove(final BytesValue prefix, final BytesValue key) {
      transaction.remove(BytesValues.concatenate(prefix, key).getArrayUnsafe());
    }

    private BytesValue rlpEncode(final List<TransactionReceipt> receipts) {
      return RLP.encode(o -> o.writeList(receipts, TransactionReceipt::writeToWithRevertReason));
    }
  }
}
