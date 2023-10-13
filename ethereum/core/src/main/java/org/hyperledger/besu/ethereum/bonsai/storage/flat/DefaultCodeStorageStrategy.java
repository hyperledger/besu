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

package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_HASH_COUNT;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE_BY_HASH;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class DefaultCodeStorageStrategy implements CodeStorageStrategy {
  @Override
  public Optional<Bytes> getFlatCode(
      final Hash codeHash, final Hash accountHash, final SegmentedKeyValueStorage storage) {
    return storage.get(CODE_STORAGE_BY_HASH, codeHash.toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public void putFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash,
      final Bytes code) {
    final long codeHashCount = getCodeHashCount(transaction, codeHash);
    updateCodeHashCount(transaction, codeHash, codeHashCount + 1);

    if (codeHashCount == 0) {
      transaction.put(CODE_STORAGE_BY_HASH, codeHash.toArrayUnsafe(), code.toArrayUnsafe());
    }
  }

  @Override
  public void removeFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash) {
    final long codeHashCount = getCodeHashCount(transaction, codeHash);
    final long updatedCodeHashCount =
        codeHashCount > 0 ? codeHashCount - 1 : 0; // ensure count min value is 0
    updateCodeHashCount(transaction, codeHash, updatedCodeHashCount);

    if (updatedCodeHashCount <= 0) {
      transaction.remove(CODE_STORAGE_BY_HASH, codeHash.toArrayUnsafe());
    }
  }

  @Override
  public void clear(final SegmentedKeyValueStorage storage) {
    storage.clear(CODE_STORAGE_BY_HASH);
    storage.clear(CODE_HASH_COUNT);
  }

  private long getCodeHashCount(
      final SegmentedKeyValueStorageTransaction transaction, final Bytes32 codeHash) {
    return transaction
        .get(CODE_HASH_COUNT, codeHash.toArrayUnsafe())
        .map(b -> Bytes.wrap(b).toLong())
        .orElse(0L);
  }

  private void updateCodeHashCount(
      final SegmentedKeyValueStorageTransaction transaction,
      final Bytes32 codeHash,
      final long updatedCodeHashCount) {
    transaction.put(
        CODE_HASH_COUNT,
        codeHash.toArray(),
        Bytes.ofUnsignedLong(updatedCodeHashCount).trimLeadingZeros().toArrayUnsafe());
  }
}
