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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

/** Requests state (storage) Merkle trie nodes by path */
public class StorageTrieNodeDataRequest extends TrieNodeDataRequest {

  private final Hash accountHash;

  public StorageTrieNodeDataRequest(
      final Optional<TrieNodeDataRequest> parent,
      final Hash accountHash,
      final Hash nodeHash,
      final Bytes location) {
    super(parent, nodeHash, location);
    this.accountHash = accountHash;
  }

  @Override
  public List<List<Bytes>> getPaths() {
    return List.of(List.of(accountHash, CompactEncoding.encode(getLocation())));
  }

  @Override
  protected void doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {
    updater.putAccountStorageTrieNode(
        accountHash, getLocation(), getHash(), getData().orElseThrow());
  }

  @Override
  protected boolean isValidResponse(
      final SnapSyncState fastSyncState,
      final EthPeers ethPeers,
      final WorldStateProofProvider worldStateProofProvider) {
    return getData().isPresent();
  }

  @Override
  protected SnapDataRequest createChildNodeDataRequest(final Hash nodeHash, final Bytes location) {
    return new StorageTrieNodeDataRequest(Optional.of(this), accountHash, nodeHash, location);
  }

  @Override
  protected Stream<SnapDataRequest> getRequestsFromTrieNodeValue(
      final WorldStateStorage worldStateStorage,
      final Optional<Bytes> location,
      final Bytes path,
      final Bytes value) {
    if (worldStateStorage instanceof BonsaiWorldStateKeyValueStorage) {
      ((BonsaiWorldStateKeyValueStorage.Updater) worldStateStorage.updater())
          .putStorageValueBySlotHash(
              accountHash, getSlotHash(location, path), Bytes32.leftPad(RLP.decodeValue(value)))
          .commit();
    }

    return Stream.empty();
  }

  @Override
  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage.getAccountStorageTrieNode(accountHash, getLocation(), getHash());
  }

  private Hash getSlotHash(final Optional<Bytes> location, final Bytes path) {
    return Hash.wrap(
        Bytes32.wrap(
            CompactEncoding.pathToBytes(Bytes.concatenate(location.orElse(Bytes.EMPTY), path))));
  }
}
