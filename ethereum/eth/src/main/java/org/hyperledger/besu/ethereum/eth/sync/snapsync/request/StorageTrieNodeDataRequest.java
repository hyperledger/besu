/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public class StorageTrieNodeDataRequest extends TrieNodeDataRequest {

  final Hash accountHash;
  final StateTrieAccountValue accountValue;

  StorageTrieNodeDataRequest(
      final Hash nodeHash,
      final Hash accountHash,
      final StateTrieAccountValue accountValue,
      final Hash rootHash,
      final Bytes location) {
    super(nodeHash, rootHash, location);
    this.accountHash = accountHash;
    this.accountValue = accountValue;
  }

  @Override
  protected int doPersist(
      final WorldStateStorage worldStateStorage,
      final Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    updater.putAccountStorageTrieNode(getAccountHash(), getLocation(), getNodeHash(), data);
    return 1;
  }

  @Override
  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage.getAccountStorageTrieNode(
        getAccountHash(), getLocation(), getNodeHash());
  }

  @Override
  protected SnapDataRequest createChildNodeDataRequest(final Hash childHash, final Bytes location) {
    return createStorageTrieNodeDataRequest(
        childHash, getAccountHash(), accountValue, getRootHash(), location);
  }

  @Override
  protected Stream<SnapDataRequest> getRequestsFromTrieNodeValue(
      final WorldStateStorage worldStateStorage,
      final Bytes location,
      final Bytes path,
      final Bytes value) {
    return Stream.empty();
  }

  public Hash getAccountHash() {
    return accountHash;
  }

  @Override
  public List<Bytes> getTrieNodePath() {
    return List.of(accountHash, CompactEncoding.encode(getLocation()));
  }
}
