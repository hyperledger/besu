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

package org.hyperledger.besu.ethereum.trie.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.trie.bonsai.storage.flat.ArchiveFlatDbStrategy.DELETED_CODE_VALUE;
import static org.hyperledger.besu.ethereum.trie.bonsai.storage.flat.ArchiveFlatDbStrategy.calculateArchiveKeyWithMinSuffix;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.Arrays;

public class ArchiveCodeStorageStrategy implements CodeStorageStrategy {

  private final BonsaiContext context;

  public ArchiveCodeStorageStrategy(final BonsaiContext context) {
    this.context = context;
  }

  /*
   * Retrieves the code data for the given code hash and account hash and block context.
   */
  @Override
  public Optional<Bytes> getFlatCode(
      final Hash codeHash, final Hash accountHash, final SegmentedKeyValueStorage storage) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {

      // keyNearest, use MAX_BLOCK_SUFFIX in the absence of a block context:
      Bytes keyNearest =
          ArchiveFlatDbStrategy.calculateArchiveKeyWithMaxSuffix(
              context, accountHash.toArrayUnsafe());

      // use getNearest() with an account key that is suffixed by the block context
      final Optional<Bytes> codeFound =
          storage
              .getNearestTo(CODE_STORAGE, keyNearest)
              // return empty when we find a "deleted value key"
              .filter(
                  found ->
                      !Arrays.areEqual(
                          DELETED_CODE_VALUE, found.value().orElse(DELETED_CODE_VALUE)))
              // map NearestKey to Bytes-wrapped value
              .flatMap(SegmentedKeyValueStorage.NearestKeyValue::wrapBytes)
              // check codeHash to sanity check the value and ensure we have the correct nearestKey:
              .filter(b -> Hash.hash(b).equals(codeHash));

      return codeFound;
    }
  }

  /*
   * Puts the code data for the given code hash and account hash and block context.
   */
  @Override
  public void putFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash,
      final Bytes code) {
    // key suffixed with block context, or MIN_BLOCK_SUFFIX if we have no context:
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(context, accountHash.toArrayUnsafe());

    transaction.put(CODE_STORAGE, keySuffixed, code.toArrayUnsafe());
  }

  /*
   * Adds a "deleted key" code entry for the given account hash and block context.
   */
  @Override
  public void removeFlatCode(
      final SegmentedKeyValueStorageTransaction transaction,
      final Hash accountHash,
      final Hash codeHash) {
    // insert a key suffixed with block context, with 'deleted account' value
    byte[] keySuffixed = calculateArchiveKeyWithMinSuffix(context, accountHash.toArrayUnsafe());

    transaction.put(CODE_STORAGE, keySuffixed, DELETED_CODE_VALUE);
  }
}
