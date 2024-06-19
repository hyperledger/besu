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
package org.hyperledger.besu.ethereum.verkletrie;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyBatchAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.TrieKeyCachedPedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class TrieKeyPreloader {

  private final TrieKeyBatchAdapter trieKeyAdapter;

  private final Hasher hasher;

  public TrieKeyPreloader() {
    this.hasher = new PedersenHasher();
    trieKeyAdapter = new TrieKeyBatchAdapter(hasher);
    trieKeyAdapter.versionKey(
        Address.ZERO); // TODO REMOVE is just to preload the native library for performance check
  }

  public List<Bytes32> generateAccountKeyIds() {
    final List<Bytes32> keys = new ArrayList<>();
    keys.add(Parameters.VERSION_LEAF_KEY);
    keys.add(Parameters.BALANCE_LEAF_KEY);
    keys.add(Parameters.NONCE_LEAF_KEY);
    keys.add(Parameters.CODE_KECCAK_LEAF_KEY);
    return keys;
  }

  public List<Bytes32> generateCodeChunkKeyIds(final Bytes code) {
    return new ArrayList<>(
        IntStream.range(0, trieKeyAdapter.getNbChunk(code)).mapToObj(UInt256::valueOf).toList());
  }

  public List<Bytes32> generateStorageKeyIds(final Set<StorageSlotKey> storageSlotKeys) {
    return storageSlotKeys.stream()
        .map(storageSlotKey -> storageSlotKey.getSlotKey().orElseThrow())
        .map(Bytes32::wrap)
        .toList();
  }

  public HasherContext createPreloadedHasher(
      final Address address,
      final List<Bytes32> accountKeyIds,
      final List<Bytes32> storageKeyIds,
      final List<Bytes32> codeChunkIds) {
    return new HasherContext(
        new TrieKeyCachedPedersenHasher(
            10000, trieKeyAdapter.manyTrieKeyHashes(address, accountKeyIds, storageKeyIds, codeChunkIds)),
        !storageKeyIds.isEmpty(),
        !codeChunkIds.isEmpty());
  }

  public record HasherContext(Hasher hasher, boolean hasStorageTrieKeys, boolean hasCodeTrieKeys) {}
}
