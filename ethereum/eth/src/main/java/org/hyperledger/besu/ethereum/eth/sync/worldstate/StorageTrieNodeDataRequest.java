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
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

class StorageTrieNodeDataRequest extends TrieNodeDataRequest {

  final Optional<Hash> accountHash;

  StorageTrieNodeDataRequest(
      final Hash hash, final Optional<Hash> accountHash, final Optional<Bytes> location) {
    super(RequestType.STORAGE_TRIE_NODE, hash, location);
    this.accountHash = accountHash;
  }

  @Override
  protected void doPersist(final Updater updater) {
    updater.putAccountStorageTrieNode(
        accountHash.orElse(Hash.EMPTY), getLocation().orElse(Bytes.EMPTY), getHash(), getData());
  }

  @Override
  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage.getAccountStorageTrieNode(
        accountHash.orElse(Hash.EMPTY), getLocation().orElse(Bytes.EMPTY), getHash());
  }

  @Override
  protected NodeDataRequest createChildNodeDataRequest(
      final Hash childHash, final Optional<Bytes> location) {
    return NodeDataRequest.createStorageDataRequest(childHash, accountHash, location);
  }

  @Override
  protected Stream<NodeDataRequest> getRequestsFromTrieNodeValue(final Bytes value) {
    // Nothing to do for terminal storage node
    return Stream.empty();
  }

  public Optional<Hash> getAccountHash() {
    return accountHash;
  }

  @Override
  protected void writeTo(final RLPOutput out) {
    out.startList();
    out.writeByte(getRequestType().getValue());
    out.writeBytes(getHash());
    getAccountHash().ifPresent(out::writeBytes);
    getLocation().ifPresent(out::writeBytes);
    out.endList();
  }
}
