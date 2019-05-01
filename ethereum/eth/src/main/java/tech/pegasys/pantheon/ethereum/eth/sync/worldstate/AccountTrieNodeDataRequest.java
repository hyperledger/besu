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

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.worldstate.StateTrieAccountValue;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage.Updater;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;
import java.util.stream.Stream;

class AccountTrieNodeDataRequest extends TrieNodeDataRequest {

  AccountTrieNodeDataRequest(final Hash hash) {
    super(RequestType.ACCOUNT_TRIE_NODE, hash);
  }

  @Override
  protected void doPersist(final Updater updater) {
    updater.putAccountStateTrieNode(getHash(), getData());
  }

  @Override
  public Optional<BytesValue> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage.getAccountStateTrieNode(getHash());
  }

  @Override
  protected NodeDataRequest createChildNodeDataRequest(final Hash childHash) {
    return NodeDataRequest.createAccountDataRequest(childHash);
  }

  @Override
  protected Stream<NodeDataRequest> getRequestsFromTrieNodeValue(final BytesValue value) {
    final Stream.Builder<NodeDataRequest> builder = Stream.builder();
    final StateTrieAccountValue accountValue = StateTrieAccountValue.readFrom(RLP.input(value));
    // Add code, if appropriate
    if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
      builder.add(NodeDataRequest.createCodeRequest(accountValue.getCodeHash()));
    }
    // Add storage, if appropriate
    if (!accountValue.getStorageRoot().equals(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH)) {
      // If storage is non-empty queue download
      final NodeDataRequest storageNode =
          NodeDataRequest.createStorageDataRequest(accountValue.getStorageRoot());
      builder.add(storageNode);
    }
    return builder.build();
  }
}
