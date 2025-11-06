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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BonsaiDbTrieWriteSink implements TrieWriteSink {

  private final BonsaiWorldStateKeyValueStorage.Updater updater;

  public BonsaiDbTrieWriteSink(final BonsaiWorldStateKeyValueStorage.Updater updater) {
    this.updater = updater;
  }

  @Override
  public void putAccountInfoState(final Hash addressHash, final Bytes accountValue) {
    updater.putAccountInfoState(addressHash, accountValue);
  }

  @Override
  public void removeAccountInfoState(final Hash addressHash) {
    updater.removeAccountInfoState(addressHash);
  }

  @Override
  public void putCode(final Hash accountHash, final Hash codeHash, final Bytes code) {
    updater.putCode(accountHash, codeHash, code);
  }

  @Override
  public void removeCode(final Hash accountHash, final Hash codeHash) {
    updater.removeCode(accountHash, codeHash);
  }

  @Override
  public void putStorageValueBySlotHash(
      final Hash accountHash, final Hash slotHash, final Bytes storageValue) {
    updater.putStorageValueBySlotHash(accountHash, slotHash, storageValue);
  }

  @Override
  public void removeStorageValueBySlotHash(final Hash accountHash, final Hash slotHash) {
    updater.removeStorageValueBySlotHash(accountHash, slotHash);
  }

  @Override
  public void putAccountStateTrieNode(
      final Bytes location, final Bytes32 nodeHash, final Bytes nodeValue) {
    updater.putAccountStateTrieNode(location, nodeHash, nodeValue);
  }

  @Override
  public void putAccountStorageTrieNode(
      final Hash accountHash, final Bytes location, final Bytes32 nodeHash, final Bytes nodeValue) {
    updater.putAccountStorageTrieNode(accountHash, location, nodeHash, nodeValue);
  }
}
