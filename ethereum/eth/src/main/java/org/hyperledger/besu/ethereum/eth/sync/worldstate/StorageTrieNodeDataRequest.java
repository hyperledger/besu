/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;
import java.util.stream.Stream;

class StorageTrieNodeDataRequest extends TrieNodeDataRequest {

  StorageTrieNodeDataRequest(final Hash hash) {
    super(RequestType.STORAGE_TRIE_NODE, hash);
  }

  @Override
  protected void doPersist(final Updater updater) {
    updater.putAccountStorageTrieNode(getHash(), getData());
  }

  @Override
  public Optional<BytesValue> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage.getAccountStorageTrieNode(getHash());
  }

  @Override
  protected NodeDataRequest createChildNodeDataRequest(final Hash childHash) {
    return NodeDataRequest.createStorageDataRequest(childHash);
  }

  @Override
  protected Stream<NodeDataRequest> getRequestsFromTrieNodeValue(final BytesValue value) {
    // Nothing to do for terminal storage node
    return Stream.empty();
  }
}
