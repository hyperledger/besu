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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.trie.verkle.util.SuffixTreeEncoder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class LeafBuilder {

  private final TrieKeyFactory trieKeyFactory;
  private final HashSet<Bytes32> keysForRemoval = new HashSet<>();
  private final HashMap<Bytes32, Bytes32> nonStorageKeyValuesForUpdate = new HashMap<>();
  private final HashMap<StorageSlotKey, Pair<Bytes32, Bytes32>> storageKeyValuesForUpdate =
      new HashMap<>();

  public LeafBuilder(final TrieKeyFactory trieKeyFactory) {
    this.trieKeyFactory = trieKeyFactory;
  }

  public void generateAccountKeyForRemoval(final Address address) {
    keysForRemoval.add(trieKeyFactory.basicDataKey(address));
  }

  public void generateCodeHashKeyForRemoval(final Address address) {
    keysForRemoval.add(trieKeyFactory.codeHashKey(address));
  }

  public void generateCodeKeysForRemoval(final Address address, final Bytes code) {
    generateCodeHashKeyForRemoval(address);
    List<UInt256> codeChunks = TrieKeyUtils.chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      keysForRemoval.add(trieKeyFactory.codeChunkKey(address, UInt256.valueOf(i)));
    }
  }

  public void generateStorageKeyForRemoval(final Address address, final StorageSlotKey storageKey) {
    keysForRemoval.add(trieKeyFactory.storageKey(address, storageKey.getSlotKey().orElseThrow()));
  }

  public void generateAccountKeyValueForUpdate(
      final Address address, final long nonce, final Wei balance) {
    Bytes32 basicDataKey = trieKeyFactory.basicDataKey(address);
    Bytes32 basicDataValue;
    if ((basicDataValue = nonStorageKeyValuesForUpdate.get(basicDataKey)) == null) {
      basicDataValue = Bytes32.ZERO;
    }

    basicDataValue = SuffixTreeEncoder.setVersionInValue(basicDataValue, Bytes.of(0));
    basicDataValue = SuffixTreeEncoder.setNonceInValue(basicDataValue, Bytes.ofUnsignedLong(nonce));
    basicDataValue =
        SuffixTreeEncoder.setBalanceInValue(
            basicDataValue,
            // balance size is exactly 16 bytes
            balance.slice(16));
    nonStorageKeyValuesForUpdate.put(basicDataKey, basicDataValue);
  }

  public void generateCodeSizeKeyValueForUpdate(final Address address, final int size) {
    Bytes32 basicDataKey = trieKeyFactory.basicDataKey(address);
    Bytes32 basicDataValue;
    if ((basicDataValue = nonStorageKeyValuesForUpdate.get(basicDataKey)) == null) {
      basicDataValue = Bytes32.ZERO;
    }

    basicDataValue =
        SuffixTreeEncoder.setCodeSizeInValue(
            basicDataValue,
            // code size is exactly 3 bytes
            Bytes.ofUnsignedInt(size).slice(1));
    nonStorageKeyValuesForUpdate.put(basicDataKey, basicDataValue);
  }

  public void generateCodeHashKeyValueForUpdate(final Address address, final Hash codeHash) {
    nonStorageKeyValuesForUpdate.put(trieKeyFactory.codeHashKey(address), codeHash);
  }

  public void generateCodeKeyValuesForUpdate(
      final Address address, final Bytes code, final Hash codeHash) {
    generateCodeSizeKeyValueForUpdate(address, code.size());
    generateCodeHashKeyValueForUpdate(address, codeHash);

    List<UInt256> codeChunks = TrieKeyUtils.chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      nonStorageKeyValuesForUpdate.put(
          trieKeyFactory.codeChunkKey(address, UInt256.valueOf(i)), codeChunks.get(i));
    }
  }

  public void generateStorageKeyValueForUpdate(
      final Address address, final StorageSlotKey storageSlotKey, final Bytes32 value) {
    storageKeyValuesForUpdate.put(
        storageSlotKey,
        new Pair<>(
            trieKeyFactory.storageKey(address, storageSlotKey.getSlotKey().orElseThrow()), value));
  }

  public Set<Bytes32> getKeysForRemoval() {
    return keysForRemoval;
  }

  public Map<Bytes32, Bytes32> getNonStorageKeyValuesForUpdate() {
    return nonStorageKeyValuesForUpdate;
  }

  public Map<StorageSlotKey, Pair<Bytes32, Bytes32>> getStorageKeyValuesForUpdate() {
    return storageKeyValuesForUpdate;
  }
}
