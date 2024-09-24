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
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyBatchAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
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

public class VerkleEntryFactory {

  private final TrieKeyBatchAdapter trieKeyAdapter;
  private final HashSet<Bytes32> keysForRemoval = new HashSet<>();
  private final HashMap<Bytes32, Bytes32> nonStorageKeyValuesForUpdate = new HashMap<>();
  private final HashMap<StorageSlotKey, Pair<Bytes32, Bytes32>> storageKeyValuesForUpdate =
      new HashMap<>();

  public VerkleEntryFactory(final Hasher hasher) {
    trieKeyAdapter = new TrieKeyBatchAdapter(hasher);
  }

  public void generateAccountKeysForRemoval(final Address address) {
    keysForRemoval.add(trieKeyAdapter.basicDataKey(address));
  }

  public void generateCodeKeysForRemoval(final Address address, final Bytes code) {
    keysForRemoval.add(trieKeyAdapter.basicDataKey(address));
    keysForRemoval.add(trieKeyAdapter.codeHashKey(address));
    List<UInt256> codeChunks = trieKeyAdapter.chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      keysForRemoval.add(trieKeyAdapter.codeChunkKey(address, UInt256.valueOf(i)));
    }
  }

  public void generateStorageKeysForRemoval(
      final Address address, final StorageSlotKey storageKey) {
    keysForRemoval.add(trieKeyAdapter.storageKey(address, storageKey.getSlotKey().orElseThrow()));
  }

  public void generateAccountKeyValueForUpdate(
      final Address address, final long nonce, final Wei balance) {
    Bytes32 basicDataKey = trieKeyAdapter.basicDataKey(address);
    Bytes32 basicDataValue;
    if ((basicDataValue = nonStorageKeyValuesForUpdate.get(basicDataKey)) == null) {
      basicDataValue = Bytes32.ZERO;
    } else {
      basicDataValue = SuffixTreeEncoder.eraseVersion(basicDataValue);
      basicDataValue = SuffixTreeEncoder.eraseNonce(basicDataValue);
      basicDataValue = SuffixTreeEncoder.eraseBalance(basicDataValue);
    }

    basicDataValue = SuffixTreeEncoder.addVersionIntoValue(basicDataValue, Bytes32.ZERO);
    basicDataValue = SuffixTreeEncoder.addNonceIntoValue(basicDataValue, UInt256.valueOf(nonce));
    basicDataValue = SuffixTreeEncoder.addBalanceIntoValue(basicDataValue, balance);
    nonStorageKeyValuesForUpdate.put(basicDataKey, basicDataValue);
  }

  public void generateCodeKeyValuesForUpdate(
      final Address address, final Bytes code, final Hash codeHash) {
    Bytes32 basicDataKey = trieKeyAdapter.basicDataKey(address);
    Bytes32 basicDataValue;
    if ((basicDataValue = nonStorageKeyValuesForUpdate.get(basicDataKey)) == null) {
      basicDataValue = Bytes32.ZERO;
    } else {
      basicDataValue = SuffixTreeEncoder.eraseCodeSize(basicDataValue);
    }

    basicDataValue =
        SuffixTreeEncoder.addCodeSizeIntoValue(basicDataValue, UInt256.valueOf(code.size()));
    nonStorageKeyValuesForUpdate.put(basicDataKey, basicDataValue);
    nonStorageKeyValuesForUpdate.put(trieKeyAdapter.codeHashKey(address), codeHash);
    List<UInt256> codeChunks = trieKeyAdapter.chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      nonStorageKeyValuesForUpdate.put(
          trieKeyAdapter.codeChunkKey(address, UInt256.valueOf(i)), codeChunks.get(i));
    }
  }

  public void generateStorageKeyValueForUpdate(
      final Address address, final StorageSlotKey storageSlotKey, final Bytes32 value) {
    storageKeyValuesForUpdate.put(
        storageSlotKey,
        new Pair<>(
            trieKeyAdapter.storageKey(address, storageSlotKey.getSlotKey().orElseThrow()), value));
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
