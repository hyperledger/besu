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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A parallel implementation of StoredMerklePatriciaTrie that processes updates in parallel
 * recursively descending to any depth where BranchNodes exist and updates are sufficient.
 */
@SuppressWarnings({"rawtypes", "ThreadPriorityCheck"})
public class ParallelStoredMerklePatriciaTrie<K extends Bytes, V>
    extends StoredMerklePatriciaTrie<K, V> {

  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 10);

  private final Map<K, Optional<V>> pendingUpdates = new HashMap<>();

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

  private void processPendingUpdates(final Optional<NodeUpdater> maybeNodeUpdater) {
    if (pendingUpdates.isEmpty()) {
      return;
    }

    try {
      this.root = loadNode(root);

      if (root instanceof BranchNode<V>) {
        processInParallel(maybeNodeUpdater);
      } else {
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
    } finally {
      pendingUpdates.clear();
    }
  }

  private void processInParallel(final Optional<NodeUpdater> maybeNodeUpdater) {
    final CommitCache commitCache = new CommitCache();
    final boolean shouldCommit = maybeNodeUpdater.isPresent();

    final List<UpdateEntry<V>> entries =
        pendingUpdates.entrySet().stream()
            .map(e -> new UpdateEntry<>(bytesToPath(e.getKey()), e.getValue()))
            .toList();

    final Map<Byte, List<UpdateEntry<V>>> groupedUpdates = groupUpdatesByNibble(entries, 0);
    final BranchWrapper rootWrapper = new BranchWrapper((BranchNode<V>) root);

    processGroupsAtBranch(
        rootWrapper,
        Bytes.EMPTY,
        groupedUpdates,
        shouldCommit ? Optional.of(commitCache) : Optional.empty());

    this.root = rootWrapper.applyUpdates();

    if (maybeNodeUpdater.isPresent()) {
      commitCache.flushTo(maybeNodeUpdater.get());
      storeAndResetRoot(maybeNodeUpdater.get());
    }
  }

  /**
   * Processes update groups for a BranchNode. Small groups are batched together and processed
   * sequentially in one parallel task. Large groups are each processed in their own parallel task.
   */
  private void processGroupsAtBranch(
      final BranchWrapper wrapper,
      final Bytes location,
      final Map<Byte, List<UpdateEntry<V>>> groupedUpdates,
      final Optional<CommitCache> maybeCommitCache) {

    if (groupedUpdates.size() == 1) {
      // Optimization: If all updates go down a single child branch, process them sequentially
      // to avoid parallel stream overhead for a single task. This is common when all updated
      // keys share a long common prefix.
      final Map.Entry<Byte, List<UpdateEntry<V>>> singleEntry =
          groupedUpdates.entrySet().iterator().next();
      processGroup(
          wrapper,
          singleEntry.getKey(),
          Bytes.concatenate(location, Bytes.of(singleEntry.getKey())),
          singleEntry.getValue(),
          maybeCommitCache);
    } else {
        final List<Future<?>> futures = new ArrayList<>();
        for (final Map.Entry<Byte, List<UpdateEntry<V>>> entry : groupedUpdates.entrySet()) {
            Future<?> future = EXECUTOR.submit(() ->
                    processGroup(
                            wrapper,
                            entry.getKey(),
                            Bytes.concatenate(location, Bytes.of(entry.getKey())),
                            entry.getValue(),
                            maybeCommitCache)
            );
            futures.add(future);
        }
        for (final Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                futures.forEach(f -> f.cancel(true));
                Thread.currentThread().interrupt();
                throw new RuntimeException("Error processing large groups in parallel", e);
            }
        }
    }
  }

  private void processGroup(
      final BranchWrapper parentWrapper,
      final byte nibbleIndex,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final Node<V> currentNode = loadNode(parentWrapper.getPendingChildren().get(nibbleIndex));

    if (currentNode instanceof ExtensionNode<V> ext) {
      handleExtension(parentWrapper, nibbleIndex, ext, location, updates, maybeCommitCache);
    } else if (currentNode instanceof BranchNode<V> branch) {
      final Node<V> newBranch = handleBranch(branch, location, updates, maybeCommitCache);
      parentWrapper.setChild(nibbleIndex, newBranch);
    } else {
      handleSequentialUpdate(
          parentWrapper, nibbleIndex, currentNode, location, updates, maybeCommitCache);
    }
  }

  private void handleExtension(
      final BranchWrapper parentWrapper,
      final byte nibbleIndex,
      final ExtensionNode<V> extensionNode,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final Bytes extensionPath = extensionNode.getPath();
    final int depth = location.size();

    if (!allUpdatesMatchExtension(updates, depth, extensionPath)) {
      handleSequentialUpdate(
          parentWrapper, nibbleIndex, extensionNode, location, updates, maybeCommitCache);
      return;
    }

    final Bytes newLocation = Bytes.concatenate(location, extensionPath);
    final Node<V> childNode = loadNode(extensionNode.getChild());

    final Node<V> newChild;
    if (childNode instanceof BranchNode<V> branch) {
      newChild = handleBranch(branch, newLocation, updates, maybeCommitCache);
    } else {
      newChild = applyUpdatesSequentially(childNode, newLocation, updates, maybeCommitCache);
    }

    final Node<V> newExtension = extensionNode.replaceChild(newChild);
    commitOrHashNode(newExtension, location, maybeCommitCache);
    parentWrapper.setChild(nibbleIndex, newExtension);
  }

  private Node<V> handleBranch(
      final BranchNode<V> branchNode,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {
    final int depth = location.size();
    final Map<Byte, List<UpdateEntry<V>>> childGroups = groupUpdatesByNibble(updates, depth);
    final BranchWrapper branchWrapper = new BranchWrapper(branchNode);

    processGroupsAtBranch(branchWrapper, location, childGroups, maybeCommitCache);

    final Node<V> newBranch = branchWrapper.applyUpdates();
    commitOrHashNode(newBranch, location, maybeCommitCache);
    return newBranch;
  }

  private void handleSequentialUpdate(
      final BranchWrapper wrapper,
      final byte nibbleIndex,
      final Node<V> node,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final Node<V> updatedNode = applyUpdatesSequentially(node, location, updates, maybeCommitCache);
    wrapper.setChild(nibbleIndex, updatedNode);
  }

  /** Applies updates sequentially using the visitor pattern. */
  private Node<V> applyUpdatesSequentially(
      final Node<V> node,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final int pathOffset = location.size();
    Node<V> updatedNode = node;

    for (UpdateEntry<V> entry : updates) {
      final Bytes remainingPath = entry.path.slice(pathOffset);
      final PathNodeVisitor<V> visitor =
          entry.value.isPresent() ? getPutVisitor(entry.value.get()) : getRemoveVisitor();
      updatedNode = updatedNode.accept(visitor, remainingPath);
    }

    commitOrHashNode(updatedNode, location, maybeCommitCache);
    return updatedNode;
  }

  private boolean allUpdatesMatchExtension(
      final List<UpdateEntry<V>> updates, final int depth, final Bytes extensionPath) {
    final int extSize = extensionPath.size();
    for (UpdateEntry<V> entry : updates) {
      if (entry.path.size() < depth + extSize) {
        return false;
      }
      for (int i = 0; i < extSize; i++) {
        if (entry.path.get(depth + i) != extensionPath.get(i)) {
          return false;
        }
      }
    }
    return true;
  }

  private Map<Byte, List<UpdateEntry<V>>> groupUpdatesByNibble(
      final List<UpdateEntry<V>> updates, final int depth) {
    return updates.stream().collect(Collectors.groupingBy(entry -> entry.getNibble(depth)));
  }

  private void commitOrHashNode(
      final Node<V> node, final Bytes location, final Optional<CommitCache> maybeCommitCache) {
    if (maybeCommitCache.isPresent()) {
      node.accept(
          location,
          new CommitVisitor<>(
              (loc, hash, value) -> maybeCommitCache.get().store(loc, hash, value)));
    } else {
      node.getHash();
    }
  }

  private void storeAndResetRoot(final NodeUpdater nodeUpdater) {
    final Bytes32 rootHash = root.getHash();
    nodeUpdater.store(Bytes.EMPTY, rootHash, root.getEncodedBytes());
    this.root =
        rootHash.equals(EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode<>(nodeFactory, Bytes.EMPTY, rootHash);
  }

  private Node<V> loadNode(final Node<V> node) {
    return node.accept(
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
  }

  /** Represents a pending update entry with its path and optional value. */
  private record UpdateEntry<V>(Bytes path, Optional<V> value) {
    byte getNibble(final int index) {
      return index >= path.size() ? 0 : path.get(index);
    }
  }

  private class BranchWrapper {
    private final BranchNode<V> originalBranch;
    private final List<Node<V>> pendingChildren;

    BranchWrapper(final BranchNode<V> branch) {
      this.originalBranch = branch;
      this.pendingChildren = Collections.synchronizedList(new ArrayList<>(branch.getChildren()));
    }

    List<Node<V>> getPendingChildren() {
      return pendingChildren;
    }

    void setChild(final byte index, final Node<V> child) {
      pendingChildren.set(index, child);
    }

    Node<V> applyUpdates() {
      return originalBranch.replaceAllChildren(pendingChildren, true);
    }
  }

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

    private record NodeData(Bytes32 hash, Bytes encodedBytes) {}
  }
}
