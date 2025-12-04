/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.patricia;

import static org.hyperledger.besu.ethereum.trie.CompactEncoding.bytesToPath;

import org.hyperledger.besu.ethereum.trie.CommitVisitor;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.PathNodeVisitor;
import org.hyperledger.besu.ethereum.trie.StoredNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A parallel implementation of StoredMerklePatriciaTrie that processes updates in parallel
 * recursively descending to any depth where BranchNodes exist and updates are sufficient. Uses a
 * deferred commit strategy to avoid synchronization bottlenecks.
 *
 * <p>This implementation uses virtual threads for efficient parallel processing. It descends
 * through ExtensionNodes to find BranchNodes for optimal parallelization.
 *
 * @param <K> The type of keys
 * @param <V> The type of values stored by this trie
 */
@SuppressWarnings("rawtypes")
public class ParallelStoredMerklePatriciaTrie<K extends Bytes, V>
    extends StoredMerklePatriciaTrie<K, V> {

  private static final ExecutorService VIRTUAL_POOL = Executors.newVirtualThreadPerTaskExecutor();

  private final Map<K, Optional<V>> pendingUpdates = new HashMap<>();

  // Constructors
  public ParallelStoredMerklePatriciaTrie(
      final NodeLoader nodeLoader,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    super(nodeLoader, valueSerializer, valueDeserializer);
  }

  public ParallelStoredMerklePatriciaTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Bytes rootLocation,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    super(nodeLoader, rootHash, rootLocation, valueSerializer, valueDeserializer);
  }

  public ParallelStoredMerklePatriciaTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    super(nodeLoader, rootHash, valueSerializer, valueDeserializer);
  }

  public ParallelStoredMerklePatriciaTrie(
      final StoredNodeFactory<V> nodeFactory, final Bytes32 rootHash) {
    super(nodeFactory, rootHash);
  }

  @Override
  public void put(final K key, final V value) {
    pendingUpdates.put(key, Optional.of(value));
  }

  @Override
  public void remove(final K key) {
    pendingUpdates.put(key, Optional.empty());
  }

  @Override
  public void commit(final NodeUpdater nodeUpdater) {
    processPendingUpdates(Optional.of(nodeUpdater));
  }

  @Override
  public Bytes32 getRootHash() {
    if (pendingUpdates.isEmpty()) {
      return root.getHash();
    }
    processPendingUpdates(Optional.empty());
    return root.getHash();
  }

  // Update entry structure
  private record UpdateEntry<V>(Bytes path, Optional<V> value) {
    byte getNibble(final int index) {
      return index >= path.size() ? 0 : path.get(index);
    }
  }

  // Node information after descent
  private record NodeInfo<V>(Node<V> node, Bytes location, int depth) {}

  // Main entry point
  private void processPendingUpdates(final Optional<NodeUpdater> maybeNodeUpdater) {
    if (pendingUpdates.isEmpty()) {
      return;
    }

    try {
      if (loadRootNode() instanceof BranchNode<V>) {
        processUpdatesInParallel(maybeNodeUpdater);
      } else {
        processUpdatesSequentially(maybeNodeUpdater);
      }
    } finally {
      pendingUpdates.clear();
    }
  }

  private void processUpdatesInParallel(final Optional<NodeUpdater> maybeNodeUpdater) {
    final CommitCache commitCache = new CommitCache();

    final List<UpdateEntry<V>> updateEntries =
        pendingUpdates.entrySet().stream()
            .map(entry -> new UpdateEntry<>(bytesToPath(entry.getKey()), entry.getValue()))
            .toList();

    final Map<Byte, List<UpdateEntry<V>>> groupedByNibble =
        updateEntries.stream().collect(Collectors.groupingBy(entry -> entry.getNibble(0)));

    final BranchWrapper rootWrapper = new BranchWrapper((BranchNode<V>) root);
    final List<CompletableFuture<Void>> futures = new ArrayList<>();

    // Process each group in parallel
    for (Map.Entry<Byte, List<UpdateEntry<V>>> entry : groupedByNibble.entrySet()) {
      final byte nibble = entry.getKey();
      final List<UpdateEntry<V>> updates = entry.getValue();

      futures.add(
          CompletableFuture.runAsync(
              () ->
                  processGroupRecursively(
                      rootWrapper,
                      nibble,
                      Bytes.of(nibble),
                      updates,
                      1,
                      maybeNodeUpdater.isPresent() ? Optional.of(commitCache) : Optional.empty()),
              VIRTUAL_POOL));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    this.root = rootWrapper.applyUpdates();

    if (maybeNodeUpdater.isPresent()) {
      commitCache.flushTo(maybeNodeUpdater.get());
      storeAndResetRoot(maybeNodeUpdater.get());
    }
  }

  private void processGroupRecursively(
      final BranchWrapper parentWrapper,
      final byte nibbleIndex,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final int depth,
      final Optional<CommitCache> maybeCommitCache) {

    Node<V> currentNode = parentWrapper.getPendingChildren().get(nibbleIndex);

    NodeInfo<V> nodeInfo = null;
    if (currentNode instanceof ExtensionNode<V>) {
      nodeInfo = descendThroughExtensions((ExtensionNode<V>) currentNode, location, depth);
      currentNode = nodeInfo.node;
    }

    // Process based on node type
    if (currentNode instanceof BranchNode<V> branchNode) {
      // Parallel descent into BranchNode
      final Bytes actualLocation = nodeInfo != null ? nodeInfo.location : location;
      final int actualDepth = nodeInfo != null ? nodeInfo.depth : depth;

      processParallelDescent(
          parentWrapper,
          nibbleIndex,
          branchNode,
          actualLocation,
          updates,
          actualDepth,
          maybeCommitCache);
    } else {
      // Sequential processing for other cases
      processNodeUpdatesSequentially(
          parentWrapper, nibbleIndex, currentNode, location, updates, depth, maybeCommitCache);
    }
  }

  private NodeInfo<V> descendThroughExtensions(
      final ExtensionNode<V> startNode, final Bytes startLocation, final int startDepth) {

    Bytes currentLocation = Bytes.concatenate(startLocation, startNode.getPath());
    int currentDepth = startDepth + startNode.getPath().size();
    Node<V> currentNode = startNode.getChild();

    return new NodeInfo<>(currentNode, currentLocation, currentDepth);
  }

  private void processParallelDescent(
      final BranchWrapper parentWrapper,
      final byte parentNibbleIndex,
      final BranchNode<V> branchNode,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final int depth,
      final Optional<CommitCache> maybeCommitCache) {

    final Map<Byte, List<UpdateEntry<V>>> groupedByNextNibble =
        updates.stream().collect(Collectors.groupingBy(entry -> entry.getNibble(depth)));

    final BranchWrapper branchWrapper = new BranchWrapper(branchNode);
    final List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (Map.Entry<Byte, List<UpdateEntry<V>>> entry : groupedByNextNibble.entrySet()) {
      final byte nibble = entry.getKey();
      final List<UpdateEntry<V>> childUpdates = entry.getValue();
      final Bytes childLocation = Bytes.concatenate(location, Bytes.of(nibble));

      futures.add(
          CompletableFuture.runAsync(
              () ->
                  processGroupRecursively(
                      branchWrapper,
                      nibble,
                      childLocation,
                      childUpdates,
                      depth + 1,
                      maybeCommitCache),
              VIRTUAL_POOL));
    }

    if (!futures.isEmpty()) {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    parentWrapper.setChildren(parentNibbleIndex, branchWrapper.applyUpdates());
  }

  private void processNodeUpdatesSequentially(
      final BranchWrapper nodeWrapper,
      final byte nibbleIndex,
      final Node<V> node,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final int pathSliceOffset,
      final Optional<CommitCache> maybeCommitCache) {

    Node<V> tmpNode = node;

    for (final UpdateEntry<V> entry : updates) {
      final Bytes path = entry.path.slice(pathSliceOffset);

      final PathNodeVisitor<V> visitor =
          entry.value.isPresent() ? getPutVisitor(entry.value.get()) : getRemoveVisitor();

      tmpNode = tmpNode.accept(visitor, path);
    }

    if (maybeCommitCache.isPresent()) {
      tmpNode.accept(
          location,
          new CommitVisitor<>(
              (loc, hash, value) -> maybeCommitCache.get().store(loc, hash, value)));
    } else {
      Objects.requireNonNull(tmpNode.getHash());
    }

    nodeWrapper.setChildren(nibbleIndex, tmpNode);
  }

  private void processUpdatesSequentially(final Optional<NodeUpdater> maybeNodeUpdater) {
    pendingUpdates.forEach(
        (key, value) -> {
          if (value.isPresent()) {
            super.put(key, value.get());
          } else {
            super.remove(key);
          }
        });
    maybeNodeUpdater.ifPresent(super::commit);
  }

  private void storeAndResetRoot(final NodeUpdater nodeUpdater) {
    final Bytes32 rootHash = root.getHash();
    nodeUpdater.store(Bytes.EMPTY, rootHash, root.getEncodedBytes());
    this.root =
        rootHash.equals(EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode<>(nodeFactory, Bytes.EMPTY, rootHash);
  }

  private Node<V> loadRootNode() {
    this.root =
        this.root.accept(
            new PathNodeVisitor<V>() {
              @Override
              public Node<V> visit(final ExtensionNode<V> extensionNode, final Bytes path) {
                return extensionNode;
              }

              @Override
              public Node<V> visit(final BranchNode<V> branchNode, final Bytes path) {
                return branchNode;
              }

              @Override
              public Node<V> visit(final LeafNode<V> leafNode, final Bytes path) {
                return leafNode;
              }

              @Override
              public Node<V> visit(final NullNode<V> nullNode, final Bytes path) {
                return nullNode;
              }
            },
            Bytes.EMPTY);
    return this.root;
  }

  // Thread-safe wrapper for BranchNode
  private class BranchWrapper {
    private final BranchNode<V> root;
    private final List<Node<V>> pendingChildren;

    public BranchWrapper(final BranchNode<V> root) {
      this.root = root;
      this.pendingChildren = Collections.synchronizedList(new ArrayList<>(root.getChildren()));
    }

    public List<Node<V>> getPendingChildren() {
      return pendingChildren;
    }

    public void setChildren(final byte index, final Node<V> children) {
      this.pendingChildren.set(index, children);
    }

    public Node<V> applyUpdates() {
      return this.root.replaceAllChildren(pendingChildren, true);
    }
  }

  // Commit cache for deferred writes
  private static class CommitCache {
    private final Map<Bytes, NodeData> cache = new ConcurrentHashMap<>();

    void store(final Bytes location, final Bytes32 hash, final Bytes encodedBytes) {
      cache.put(location, new NodeData(hash, encodedBytes));
    }

    void flushTo(final NodeUpdater nodeUpdater) {
      cache.forEach(
          (location, nodeData) ->
              nodeUpdater.store(location, nodeData.hash, nodeData.encodedBytes));
    }

    private static class NodeData {
      final Bytes32 hash;
      final Bytes encodedBytes;

      NodeData(final Bytes32 hash, final Bytes encodedBytes) {
        this.hash = hash;
        this.encodedBytes = encodedBytes;
      }
    }
  }
}
