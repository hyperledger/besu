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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.TRIE_NODE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.TasksPriorityProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public abstract class TrieNodeDataRequest extends SnapDataRequest implements TasksPriorityProvider {

  private final Bytes32 nodeHash;
  private final Bytes location;
  protected Bytes data;

  protected boolean requiresPersisting = true;

  protected TrieNodeDataRequest(final Hash nodeHash, final Hash rootHash, final Bytes location) {
    super(TRIE_NODE, rootHash);
    this.nodeHash = nodeHash;
    this.location = location;
    this.data = Bytes.EMPTY;
  }

  @Override
  public int persist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    if (isExpired(snapSyncState) || pendingChildren.get() > 0) {
      // we do nothing. Our last child will eventually persist us.
      return 0;
    }
    int saved = 0;
    if (requiresPersisting) {
      checkNotNull(data, "Must set data before node can be persisted.");
      saved = doPersist(worldStateStorage, updater, downloadState, snapSyncState);
    }
    if (possibleParent.isPresent()) {
      return possibleParent
              .get()
              .saveParent(worldStateStorage, updater, downloadState, snapSyncState)
          + saved;
    }
    return saved;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState) {
    if (!isResponseReceived()) {
      // If this node hasn't been downloaded yet, we can't return any child data
      return Stream.empty();
    }

    final List<Node<Bytes>> nodes = TrieNodeDecoder.decodeNodes(location, data);
    return nodes.stream()
        .flatMap(
            node -> {
              if (nodeIsHashReferencedDescendant(node)) {
                return Stream.of(
                    createChildNodeDataRequest(
                        Hash.wrap(node.getHash()), node.getLocation().orElse(Bytes.EMPTY)));
              } else {
                return node.getValue()
                    .map(
                        value ->
                            getRequestsFromTrieNodeValue(
                                worldStateStorage,
                                node.getLocation().orElse(Bytes.EMPTY),
                                node.getPath(),
                                value))
                    .orElseGet(Stream::empty);
              }
            })
        .peek(request -> request.registerParent(this));
  }

  public boolean isRoot() {
    return possibleParent.isEmpty();
  }

  @Override
  public boolean isResponseReceived() {
    return !data.isEmpty() && Hash.hash(data).equals(getNodeHash());
  }

  @Override
  public boolean isExpired(final SnapSyncState snapSyncState) {
    return snapSyncState.isExpired(this);
  }

  public boolean isRequiresPersisting() {
    return requiresPersisting;
  }

  public Bytes32 getNodeHash() {
    return nodeHash;
  }

  public Bytes getLocation() {
    return location;
  }

  @Override
  public int getDepth() {
    return depth;
  }

  @Override
  public long getPriority() {
    return priority;
  }

  public Bytes getPathId() {
    return Bytes.concatenate(new ArrayList<>(getTrieNodePath()));
  }

  public void setData(final Bytes data) {
    this.data = data;
  }

  public void setRequiresPersisting(final boolean requiresPersisting) {
    this.requiresPersisting = requiresPersisting;
  }

  private boolean nodeIsHashReferencedDescendant(final Node<Bytes> node) {
    return !Objects.equals(node.getHash(), nodeHash) && node.isReferencedByHash();
  }

  public abstract Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage);

  public abstract List<Bytes> getTrieNodePath();

  protected abstract SnapDataRequest createChildNodeDataRequest(
      final Hash childHash, final Bytes location);

  public Stream<SnapDataRequest> getRootStorageRequests(final WorldStateStorage worldStateStorage) {
    return Stream.empty();
  }

  protected abstract Stream<SnapDataRequest> getRequestsFromTrieNodeValue(
      final WorldStateStorage worldStateStorage,
      final Bytes location,
      final Bytes path,
      final Bytes value);
}
