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
package org.hyperledger.besu.ethereum.verkletrie;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.identityconnectors.common.logging.Log;

public class VerkleTrieKeyValueGenerator {

  final TrieKeyAdapter trieKeyAdapter = new TrieKeyAdapter(new PedersenHasher());

  public Map<Bytes, Bytes> generateKeyValuesForAccount(
      final Address address, final long nonce, final Wei balance, final Hash codeHash) {
    final Map<Bytes, Bytes> keyValues = new HashMap<>();
    keyValues.put(trieKeyAdapter.versionKey(address), Bytes32.ZERO);
    keyValues.put(trieKeyAdapter.balanceKey(address), toLittleEndian(balance));
    keyValues.put(trieKeyAdapter.nonceKey(address), toLittleEndian(UInt256.valueOf(nonce)));
    keyValues.put(trieKeyAdapter.codeKeccakKey(address), codeHash);
    return keyValues;
  }

  public List<Bytes> generateKeysForAccount(final Address address) {
    final List<Bytes> keys = new ArrayList<>();
    keys.add(trieKeyAdapter.versionKey(address));
    keys.add(trieKeyAdapter.balanceKey(address));
    keys.add(trieKeyAdapter.nonceKey(address));
    return keys;
  }

  public Map<Bytes, Bytes> generateKeyValuesForCode(

      final Address address, final Bytes32 keccakCodeHash, final Bytes code) {

    final Map<Bytes, Bytes> keyValues = new HashMap<>();
    keyValues.put(
        trieKeyAdapter.codeSizeKey(address), toLittleEndian(UInt256.valueOf(code.size())));
    List<Bytes32> codeChunks = trieKeyAdapter.chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      // System.out.println("add code " + trieKeyAdapter.codeChunkKey(address, UInt256.valueOf(i)) +
      // " " +  codeChunks.get(i));
      keyValues.put(trieKeyAdapter.codeChunkKey(address, UInt256.valueOf(i)), codeChunks.get(i));
    }
    return keyValues;
  }

  public List<Bytes> generateKeysForCode(final Address address, final Bytes code) {
    final List<Bytes> keys = new ArrayList<>();
    keys.add(trieKeyAdapter.codeKeccakKey(address));
    keys.add(trieKeyAdapter.codeSizeKey(address));
    List<Bytes32> codeChunks = trieKeyAdapter.chunkifyCode(code);
    for (int i = 0; i < codeChunks.size(); i++) {
      keys.add(trieKeyAdapter.codeChunkKey(address, UInt256.valueOf(i)));
    }
    return keys;
  }

  public Pair<Bytes, Bytes> generateKeyValuesForStorage(
      final Address address, final StorageSlotKey storageKey, final Bytes value) {
    return new Pair<>(
            trieKeyAdapter.storageKey(address, storageKey.getSlotKey().orElseThrow()), value);
  }

  public List<Bytes> generateKeysForStorage(
      final Address address, final StorageSlotKey storageKey) {
    return List.of(trieKeyAdapter.storageKey(address, storageKey.getSlotKey().orElseThrow()));
  }

  private static Bytes toLittleEndian(final Bytes originalValue) {
    return originalValue.reverse();
  }
}
