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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.TRIE_NODE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetTrieNodes;
import org.hyperledger.besu.ethereum.eth.messages.snap.TrieNodes;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.TrieNodeDecoder;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;
import org.hyperledger.besu.services.tasks.Task;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;

public abstract class TrieNodeDataRequest extends SnapDataRequest {

  protected Optional<TrieNodeDataRequest> parent;
  protected long nbMissingChild;

  private Bytes location;
  private Hash nodeHash;

  public TrieNodeDataRequest() {
    super(TRIE_NODE);
  }

  protected TrieNodeDataRequest(
      final Optional<TrieNodeDataRequest> parent, final Hash nodeHash, final Bytes location) {
    super(TRIE_NODE);
    this.parent = parent;
    this.nodeHash = nodeHash;
    this.location = location;
    this.nbMissingChild = 0;
  }

  public boolean hasMissingChild() {
    synchronized (this) {
      return nbMissingChild > 0;
    }
  }

  public void incNbMissingChild() {
    synchronized (this) {
      this.nbMissingChild++;
    }
  }

  public void notifyChildFound() {
    synchronized (this) {
      this.nbMissingChild--;
    }
  }

  public Bytes getLocation() {
    return location;
  }

  public Hash getHash() {
    return nodeHash;
  }

  @Override
  public final void persist(final WorldStateStorage worldStateStorage) {
    if (!hasMissingChild()) {
      final Updater updater = worldStateStorage.updater();
      doPersist(worldStateStorage, updater);
      updater.commit();
      parent.ifPresent(
          trieNodeDataRequest -> {
            trieNodeDataRequest.notifyChildFound();
            trieNodeDataRequest.persist(worldStateStorage);
          });
    }
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(final WorldStateStorage worldStateStorage) {

    final List<Node<Bytes>> nodes = TrieNodeDecoder.decodeNodes(location, getData().orElseThrow());
    return nodes.stream()
        .flatMap(
            node -> {
              if (nodeIsHashReferencedDescendant(node)) {
                incNbMissingChild();
                return Stream.of(
                    createChildNodeDataRequest(
                        Hash.wrap(node.getHash()), node.getLocation().orElse(Bytes.EMPTY)));
              } else {
                return node.getValue()
                    .map(
                        value ->
                            getRequestsFromTrieNodeValue(
                                worldStateStorage, node.getLocation(), node.getPath(), value))
                    .orElseGet(Stream::empty);
              }
            });
  }

  public static GetTrieNodes create(
      final Hash worldStateRootHash, final TrieNodeDataRequest request) {
    return GetTrieNodes.createWithRequest(worldStateRootHash, request, BigInteger.valueOf(524288));
  }

  private boolean nodeIsHashReferencedDescendant(final Node<Bytes> node) {
    return !Objects.equals(node.getHash(), getHash()) && node.isReferencedByHash();
  }

  @Override
  public SnapDataRequest setData(final Bytes data) {
    super.setData(Optional.ofNullable(data));
    return this;
  }

  @Override
  public SnapDataRequest setData(final Optional<Bytes> maybeData) {
    super.setData(maybeData);
    return this;
  }

  public static TrieNodeDataRequest compactPaths(final List<Task<SnapDataRequest>> requests) {
    final List<List<Bytes>> paths =
        requests.stream()
            .map(Task::getData)
            .map(TrieNodeDataRequest.class::cast)
            .map(TrieNodeDataRequest::getPaths)
            .flatMap(Collection::stream)
            .distinct()
            .collect(Collectors.toList());
    return new TrieNodeDataRequest() {
      @Override
      public List<List<Bytes>> getPaths() {
        return paths;
      }
    };
  }

  public static ArrayDeque<Bytes> formatTrieNodes(
      final List<Task<SnapDataRequest>> requestTasks, final Bytes data) {
    return new TrieNodes(data).nodes(true);
  }

  public abstract List<List<Bytes>> getPaths();

  protected SnapDataRequest createChildNodeDataRequest(final Hash nodeHash, final Bytes location) {
    throw new NoSuchElementException("not available for a generic trie node data request");
  }

  protected Stream<SnapDataRequest> getRequestsFromTrieNodeValue(
      final WorldStateStorage worldStateStorage,
      final Optional<Bytes> location,
      final Bytes path,
      final Bytes value) {
    throw new NoSuchElementException("not available for a generic trie node data request");
  }

  @Override
  protected void doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {
    throw new NoSuchElementException("not available for a generic trie node data request");
  }

  @Override
  protected boolean isValidResponse(
      final SnapSyncState fastSyncState,
      final EthPeers ethPeers,
      final WorldStateProofProvider worldStateProofProvider) {
    throw new NoSuchElementException("not available for a generic trie node data request");
  }

  @Override
  public void clear() {}

  @Override
  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    throw new NoSuchElementException("not available for a generic trie node data request");
  }
}
