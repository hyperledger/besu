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
package org.hyperledger.besu.ethereum.privacy.storage;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateStateKeyValueStorage implements PrivateStateStorage {

  public static final int SCHEMA_VERSION_1_0_0 = 1;
  public static final int SCHEMA_VERSION_1_4_0 = 2;

  private static final Bytes DB_VERSION_KEY = Bytes.of("DBVERSION".getBytes(UTF_8));
  private static final Bytes TX_RECEIPT_SUFFIX = Bytes.of("RECEIPT".getBytes(UTF_8));
  private static final Bytes METADATA_KEY_SUFFIX = Bytes.of("METADATA".getBytes(UTF_8));
  private static final Bytes PRIVACY_GROUP_HEAD_BLOCK_MAP_SUFFIX =
      Bytes.of("PGHEADMAP".getBytes(UTF_8));
  private static final Bytes LEGACY_STATUS_KEY_SUFFIX = Bytes.of("STATUS".getBytes(UTF_8));
  private static final Bytes ADD_DATA_KEY = Bytes.of("ADDKEY".getBytes(UTF_8));

  private final KeyValueStorage keyValueStorage;

  public PrivateStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<PrivateTransactionReceipt> getTransactionReceipt(
      final Bytes32 blockHash, final Bytes32 pmtHash) {
    final Bytes blockHashTxHash = Bytes.concatenate(blockHash, pmtHash);
    return get(blockHashTxHash, TX_RECEIPT_SUFFIX)
        .map(b -> PrivateTransactionReceipt.readFrom(new BytesValueRLPInput(b, false)));
  }

  @Override
  public Optional<PrivateBlockMetadata> getPrivateBlockMetadata(
      final Bytes32 blockHash, final Bytes32 privacyGroupId) {
    return get(Bytes.concatenate(blockHash, privacyGroupId), METADATA_KEY_SUFFIX)
        .map(this::rlpDecodePrivateBlockMetadata);
  }

  @Override
  public Optional<PrivacyGroupHeadBlockMap> getPrivacyGroupHeadBlockMap(final Bytes32 blockHash) {
    return get(blockHash, PRIVACY_GROUP_HEAD_BLOCK_MAP_SUFFIX)
        .map(b -> PrivacyGroupHeadBlockMap.readFrom(new BytesValueRLPInput(b, false)));
  }

  @Override
  public Optional<Bytes32> getAddDataKey(final Bytes32 privacyGroupId) {
    return get(privacyGroupId, ADD_DATA_KEY).map(Bytes32::wrap);
  }

  @Override
  public int getSchemaVersion() {
    return get(Bytes.EMPTY, DB_VERSION_KEY).map(Bytes::toInt).orElse(SCHEMA_VERSION_1_0_0);
  }

  @Override
  public boolean isEmpty() {
    return keyValueStorage
        .getAllKeysThat(
            containsSuffix(LEGACY_STATUS_KEY_SUFFIX)
                .or(containsSuffix(TX_RECEIPT_SUFFIX))
                .or(containsSuffix(METADATA_KEY_SUFFIX)))
        .isEmpty();
  }

  private Predicate<byte[]> containsSuffix(final Bytes suffix) {
    final byte[] suffixArray = suffix.toArrayUnsafe();
    return key ->
        key.length > suffixArray.length
            && Arrays.equals(
                Arrays.copyOfRange(key, key.length - suffixArray.length, key.length), suffixArray);
  }

  private Optional<Bytes> get(final Bytes key, final Bytes keySuffix) {
    return keyValueStorage.get(Bytes.concatenate(key, keySuffix).toArrayUnsafe()).map(Bytes::wrap);
  }

  private PrivateBlockMetadata rlpDecodePrivateBlockMetadata(final Bytes bytes) {
    return PrivateBlockMetadata.readFrom(RLP.input(bytes));
  }

  @Override
  public PrivateStateStorage.Updater updater() {
    return new PrivateStateKeyValueStorage.Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements PrivateStateStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public PrivateStateStorage.Updater putTransactionReceipt(
        final Bytes32 blockHash,
        final Bytes32 transactionHash,
        final PrivateTransactionReceipt receipt) {
      final Bytes blockHashTxHash = Bytes.concatenate(blockHash, transactionHash);
      set(blockHashTxHash, TX_RECEIPT_SUFFIX, RLP.encode(receipt::writeTo));
      return this;
    }

    @Override
    public PrivateStateStorage.Updater putPrivateBlockMetadata(
        final Bytes32 blockHash,
        final Bytes32 privacyGroupId,
        final PrivateBlockMetadata metadata) {
      set(
          Bytes.concatenate(blockHash, privacyGroupId),
          METADATA_KEY_SUFFIX,
          RLP.encode(metadata::writeTo));
      return this;
    }

    @Override
    public PrivateStateStorage.Updater putPrivacyGroupHeadBlockMap(
        final Bytes32 blockHash, final PrivacyGroupHeadBlockMap map) {
      set(blockHash, PRIVACY_GROUP_HEAD_BLOCK_MAP_SUFFIX, RLP.encode(map::writeTo));
      return this;
    }

    @Override
    public PrivateStateStorage.Updater putDatabaseVersion(final int version) {
      set(Bytes.EMPTY, DB_VERSION_KEY, Bytes.ofUnsignedInt(version));
      return this;
    }

    @Override
    public PrivateStateStorage.Updater putAddDataKey(
        final Bytes32 privacyGroupId, final Bytes32 addDataKey) {
      set(privacyGroupId, ADD_DATA_KEY, addDataKey);
      return this;
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }

    private void set(final Bytes key, final Bytes keySuffix, final Bytes value) {
      transaction.put(Bytes.concatenate(key, keySuffix).toArrayUnsafe(), value.toArrayUnsafe());
    }

    @Override
    public void remove(final Bytes key, final Bytes keySuffix) {
      transaction.remove(Bytes.concatenate(key, keySuffix).toArrayUnsafe());
    }
  }
}
