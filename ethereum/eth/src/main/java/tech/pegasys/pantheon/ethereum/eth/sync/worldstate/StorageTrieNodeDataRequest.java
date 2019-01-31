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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage.Updater;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.List;

class StorageTrieNodeDataRequest extends TrieNodeDataRequest {

  StorageTrieNodeDataRequest(final Hash hash) {
    super(RequestType.STORAGE_TRIE_NODE, hash);
  }

  @Override
  public void persist(final Updater updater) {
    checkNotNull(getData(), "Must set data before node can be persisted.");
    updater.putAccountStorageTrieNode(getHash(), getData());
  }

  @Override
  protected NodeDataRequest createChildNodeDataRequest(final Hash childHash) {
    return NodeDataRequest.createStorageDataRequest(childHash);
  }

  @Override
  protected List<NodeDataRequest> getRequestsFromTrieNodeValue(final BytesValue value) {
    // Nothing to do for terminal storage node
    return Collections.emptyList();
  }
}
