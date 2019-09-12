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
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import tech.pegasys.pantheon.ethereum.chain.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.chain.TransactionLocation;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;
import tech.pegasys.pantheon.util.uint.UInt256;
import tech.pegasys.pantheon.util.uint.UInt256Bytes;

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
