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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * A parallel implementation of StoredMerklePatriciaTrie that processes updates in parallel.
 *
 * <p>This implementation batches updates and processes them concurrently when the root is a
 * BranchNode and there are sufficient updates to warrant parallel processing. The parallelization
 * strategy recursively descends the trie structure, processing independent branches concurrently.
 *
 * @param <K> the key type, must extend Bytes
 * @param <V> the value type
 */
@SuppressWarnings({"rawtypes", "ThreadPriorityCheck"})
public class ParallelStoredMerklePatriciaTrie<K extends Bytes, V>
    extends StoredMerklePatriciaTrie<K, V> {

  private static final int NCPU = Runtime.getRuntime().availableProcessors();

  /** Shared executor service using ForkJoinPool with 2x cores for I/O-bound operations */
  private static final ExecutorService FORK_JOIN_POOL = new ForkJoinPool(NCPU * 2);

  /** Pending updates accumulated between commits */
  private final Map<K, Optional<V>> pendingUpdates = new ConcurrentHashMap<>();

  /**
   * Creates a new parallel trie with an empty root.
   *
   * @param nodeLoader the node loader for retrieving stored nodes
   * @param valueSerializer function to serialize values to bytes
   * @param valueDeserializer function to deserialize bytes to values
   */
  public ParallelStoredMerklePatriciaTrie(
      final NodeLoader nodeLoader,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    super(nodeLoader, valueSerializer, valueDeserializer);
  }

  /**
   * Creates a new parallel trie with a specific root hash and location.
   *
   * @param nodeLoader the node loader for retrieving stored nodes
   * @param rootHash the hash of the root node
   * @param rootLocation the storage location of the root node
   * @param valueSerializer function to serialize values to bytes
   * @param valueDeserializer function to deserialize bytes to values
   */
  public ParallelStoredMerklePatriciaTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Bytes rootLocation,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    super(nodeLoader, rootHash, rootLocation, valueSerializer, valueDeserializer);
  }

  /**
   * Creates a new parallel trie with a specific root hash.
   *
   * @param nodeLoader the node loader for retrieving stored nodes
   * @param rootHash the hash of the root node
   * @param valueSerializer function to serialize values to bytes
   * @param valueDeserializer function to deserialize bytes to values
   */
  public ParallelStoredMerklePatriciaTrie(
      final NodeLoader nodeLoader,
      final Bytes32 rootHash,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    super(nodeLoader, rootHash, valueSerializer, valueDeserializer);
  }

  /**
   * Creates a new parallel trie with a node factory and root hash.
   *
   * @param nodeFactory the factory for creating nodes
   * @param rootHash the hash of the root node
   */
  public ParallelStoredMerklePatriciaTrie(
      final StoredNodeFactory<V> nodeFactory, final Bytes32 rootHash) {
    super(nodeFactory, rootHash);
  }

  /**
   * Stages a put operation for the given key-value pair. The update is not applied until commit()
   * or getRootHash() is called.
   *
   * @param key the key to insert
   * @param value the value to associate with the key
   */
  @Override
  public void put(final K key, final V value) {
    pendingUpdates.put(key, Optional.of(value));
  }

  /**
   * Stages a remove operation for the given key. The update is not applied until commit() or
   * getRootHash() is called.
   *
   * @param key the key to remove
   */
  @Override
  public void remove(final K key) {
    pendingUpdates.put(key, Optional.empty());
  }

  /**
   * Commits all pending updates to storage using the provided node updater. Applies updates in
   * parallel when beneficial, then persists nodes to storage.
   *
   * @param nodeUpdater the updater to persist nodes to storage
   */
  @Override
  public void commit(final NodeUpdater nodeUpdater) {
    processPendingUpdates(Optional.of(nodeUpdater));
  }

  /**
   * Computes and returns the root hash after applying all pending updates. This triggers update
   * processing but does not persist nodes to storage.
   *
   * @return the Merkle root hash of the trie
   */
  @Override
  public Bytes32 getRootHash() {
    if (pendingUpdates.isEmpty()) {
      return root.getHash();
    }
    processPendingUpdates(Optional.empty());
    return root.getHash();
  }

  /**
   * Processes all pending updates, applying them to the trie structure. Chooses between parallel
   * and sequential processing based on the root node type.
   *
   * @param maybeNodeUpdater optional node updater for persisting changes
   */
  private void processPendingUpdates(final Optional<NodeUpdater> maybeNodeUpdater) {
    if (pendingUpdates.isEmpty()) {
      return;
    }

    try {
      // Ensure root is fully loaded (not a lazy StoredNode reference)
      this.root = loadNode(root);

      if (root instanceof BranchNode<V>) {
        // Root is a branch node: parallel processing is possible
        processInParallel(maybeNodeUpdater);
      } else {
        // Root is leaf, extension, or null: process sequentially
        processSequentially(maybeNodeUpdater);
      }
    } finally {
      // Always clear pending updates after processing
      pendingUpdates.clear();
    }
  }

  /**
   * Processes updates sequentially using the parent class's implementation.
   *
   * @param maybeNodeUpdater optional node updater for persisting changes
   */
  private void processSequentially(final Optional<NodeUpdater> maybeNodeUpdater) {
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

  /**
   * Processes updates in parallel by distributing work across branch children. Creates a commit
   * cache for thread-safe storage updates when committing.
   *
   * @param maybeNodeUpdater optional node updater for persisting changes
   */
  private void processInParallel(final Optional<NodeUpdater> maybeNodeUpdater) {
    final CommitCache commitCache = new CommitCache();
    final boolean shouldCommit = maybeNodeUpdater.isPresent();

    // Convert pending updates to UpdateEntry objects with nibble paths
    final List<UpdateEntry<V>> entries =
        pendingUpdates.entrySet().stream()
            .map(e -> new UpdateEntry<>(bytesToPath(e.getKey()), e.getValue()))
            .toList();

    // Group updates by first nibble to process each branch child independently
    final Map<Byte, List<UpdateEntry<V>>> groupedUpdates = groupUpdatesByNibble(entries, 0);

    // Wrap the root branch to allow concurrent child updates
    final BranchWrapper rootWrapper = new BranchWrapper((BranchNode<V>) root);

    // Process all branch children, potentially in parallel
    processGroupsAtBranch(
        rootWrapper,
        Bytes.EMPTY,
        groupedUpdates,
        shouldCommit ? Optional.of(commitCache) : Optional.empty());

    // Apply all child updates to create the new root
    this.root = rootWrapper.applyUpdates();

    // Persist all nodes to storage if committing
    if (maybeNodeUpdater.isPresent()) {
      commitCache.flushTo(maybeNodeUpdater.get());
      storeAndResetRoot(maybeNodeUpdater.get());
    }
  }

  /**
   * Processes update groups at a branch node, parallelizing large groups.
   *
   * <p>Strategy:
   *
   * <ul>
   *   <li>Groups with >= NCPU updates: process in parallel (if multiple groups exist)
   *   <li>Groups with < NCPU updates: process sequentially in the calling thread
   * </ul>
   *
   * @param wrapper the branch wrapper to update
   * @param location the current location in the trie
   * @param groupedUpdates map of nibble -> list of updates for that branch child
   * @param maybeCommitCache optional commit cache for storing nodes
   */
  private void processGroupsAtBranch(
      final BranchWrapper wrapper,
      final Bytes location,
      final Map<Byte, List<UpdateEntry<V>>> groupedUpdates,
      final Optional<CommitCache> maybeCommitCache) {

    // Partition groups into large (parallel) and small (sequential)
    final Map<Boolean, Map<Byte, List<UpdateEntry<V>>>> partitionedGroups =
        groupedUpdates.entrySet().stream()
            .collect(
                Collectors.partitioningBy(
                    entry -> entry.getValue().size() >= NCPU && groupedUpdates.size() > 1,
                    Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

    final Map<Byte, List<UpdateEntry<V>>> largeGroups = partitionedGroups.get(true);
    final Map<Byte, List<UpdateEntry<V>>> smallGroups = partitionedGroups.get(false);

    // Submit large groups to thread pool
    final List<Future<?>> largeGroupFutures = new ArrayList<>();
    if (!largeGroups.isEmpty()) {
      for (final Map.Entry<Byte, List<UpdateEntry<V>>> entry : largeGroups.entrySet()) {
        Future<?> future =
            FORK_JOIN_POOL.submit(
                () ->
                    processGroup(
                        wrapper,
                        entry.getKey(),
                        Bytes.concatenate(location, Bytes.of(entry.getKey())),
                        entry.getValue(),
                        maybeCommitCache));
        largeGroupFutures.add(future);
      }
    }

    // Process small groups sequentially in current thread
    for (final Map.Entry<Byte, List<UpdateEntry<V>>> entry : smallGroups.entrySet()) {
      final byte nibble = entry.getKey();
      final List<UpdateEntry<V>> updates = entry.getValue();
      final Bytes childLocation = Bytes.concatenate(location, Bytes.of(nibble));
      processGroup(wrapper, nibble, childLocation, updates, maybeCommitCache);
    }

    // Wait for all parallel tasks to complete
    for (final Future<?> future : largeGroupFutures) {
      try {
        future.get();
      } catch (InterruptedException | ExecutionException e) {
        // Cancel all remaining tasks on failure
        largeGroupFutures.forEach(f -> f.cancel(true));
        Thread.currentThread().interrupt();
        throw new RuntimeException("Error processing large groups in parallel", e);
      }
    }
  }

  /**
   * Processes a single group of updates for a specific branch child. Dispatches to appropriate
   * handler based on node type.
   *
   * @param parentWrapper the parent branch wrapper
   * @param nibbleIndex the child index (0-15)
   * @param location the location of the child node
   * @param updates the updates to apply to this child
   * @param maybeCommitCache optional commit cache for storing nodes
   */
  private void processGroup(
      final BranchWrapper parentWrapper,
      final byte nibbleIndex,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    // Load the current child node (may be lazy-loaded)
    final Node<V> currentNode = loadNode(parentWrapper.getPendingChildren().get(nibbleIndex));

    // Dispatch based on node type
    if (currentNode instanceof ExtensionNode<V> ext) {
      handleExtension(parentWrapper, nibbleIndex, ext, location, updates, maybeCommitCache);
    } else if (currentNode instanceof BranchNode<V> branch) {
      final Node<V> newBranch = handleBranch(branch, location, updates, maybeCommitCache);
      parentWrapper.setChild(nibbleIndex, newBranch);
    } else {
      // LeafNode or NullNode: process sequentially
      handleOtherNode(parentWrapper, nibbleIndex, currentNode, location, updates, maybeCommitCache);
    }
  }

  /**
   * Handles updates for an extension node. If all updates match the extension path, recurse into
   * the child. Otherwise, fall back to sequential processing (the extension may need
   * restructuring).
   *
   * @param parentWrapper the parent branch wrapper
   * @param nibbleIndex the child index in the parent
   * @param extensionNode the extension node to update
   * @param location the location of the extension node
   * @param updates the updates to apply
   * @param maybeCommitCache optional commit cache for storing nodes
   */
  private void handleExtension(
      final BranchWrapper parentWrapper,
      final byte nibbleIndex,
      final ExtensionNode<V> extensionNode,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final Bytes extensionPath = extensionNode.getPath();
    final int depth = location.size();

    // Check if all updates pass through this extension
    if (!allUpdatesMatchExtension(updates, depth, extensionPath)) {
      // Updates diverge: must restructure, use sequential processing
      handleOtherNode(
          parentWrapper, nibbleIndex, extensionNode, location, updates, maybeCommitCache);
      return;
    }

    // All updates continue past extension: process child node
    final Bytes newLocation = Bytes.concatenate(location, extensionPath);
    final Node<V> childNode = loadNode(extensionNode.getChild());

    final Node<V> newChild;
    if (childNode instanceof BranchNode<V> branch) {
      // Child is branch: continue parallel processing
      newChild = handleBranch(branch, newLocation, updates, maybeCommitCache);
    } else {
      // Child is leaf/null: process sequentially
      newChild = applyUpdatesSequentially(childNode, newLocation, updates, maybeCommitCache);
    }

    // Create new extension with updated child
    final Node<V> newExtension = extensionNode.replaceChild(newChild);
    commitOrHashNode(newExtension, location, maybeCommitCache);
    parentWrapper.setChild(nibbleIndex, newExtension);
  }

  /**
   * Handles updates for a branch node by recursively processing its children. This is the key
   * recursion point for parallel processing.
   *
   * @param branchNode the branch node to update
   * @param location the location of the branch node
   * @param updates the updates to apply
   * @param maybeCommitCache optional commit cache for storing nodes
   * @return the updated branch node
   */
  private Node<V> handleBranch(
      final BranchNode<V> branchNode,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final int depth = location.size();

    // Group updates by next nibble to distribute across branch children
    final Map<Byte, List<UpdateEntry<V>>> childGroups = groupUpdatesByNibble(updates, depth);
    final BranchWrapper branchWrapper = new BranchWrapper(branchNode);

    // Recursively process child groups (potentially in parallel)
    processGroupsAtBranch(branchWrapper, location, childGroups, maybeCommitCache);

    // Apply all child updates
    final Node<V> newBranch = branchWrapper.applyUpdates();
    commitOrHashNode(newBranch, location, maybeCommitCache);
    return newBranch;
  }

  /**
   * Handles updates for leaf or null nodes using sequential processing. These nodes cannot be
   * parallelized further.
   *
   * @param wrapper the parent branch wrapper
   * @param nibbleIndex the child index in the parent
   * @param node the node to update
   * @param location the location of the node
   * @param updates the updates to apply
   * @param maybeCommitCache optional commit cache for storing nodes
   */
  private void handleOtherNode(
      final BranchWrapper wrapper,
      final byte nibbleIndex,
      final Node<V> node,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final Node<V> updatedNode = applyUpdatesSequentially(node, location, updates, maybeCommitCache);
    wrapper.setChild(nibbleIndex, updatedNode);
  }

  /**
   * Applies updates sequentially using the visitor pattern. This is the fallback for cases where
   * parallel processing isn't beneficial.
   *
   * @param node the starting node
   * @param location the location of the node
   * @param updates the updates to apply
   * @param maybeCommitCache optional commit cache for storing nodes
   * @return the updated node
   */
  private Node<V> applyUpdatesSequentially(
      final Node<V> node,
      final Bytes location,
      final List<UpdateEntry<V>> updates,
      final Optional<CommitCache> maybeCommitCache) {

    final int pathOffset = location.size();
    Node<V> updatedNode = node;

    // Apply each update using the visitor pattern
    for (UpdateEntry<V> entry : updates) {
      final Bytes remainingPath = entry.path.slice(pathOffset);
      final PathNodeVisitor<V> visitor =
          entry.value.isPresent() ? getPutVisitor(entry.value.get()) : getRemoveVisitor();
      updatedNode = updatedNode.accept(visitor, remainingPath);
    }

    commitOrHashNode(updatedNode, location, maybeCommitCache);
    return updatedNode;
  }

  /**
   * Checks if all updates in the list match the given extension path. An update matches if its path
   * at the given depth starts with the extension path.
   *
   * @param updates the updates to check
   * @param depth the current depth in the trie
   * @param extensionPath the extension path to match against
   * @return true if all updates match, false otherwise
   */
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

  /**
   * Groups updates by their nibble at the specified depth. Returns a map where keys are nibbles
   * (0-15) and values are lists of updates.
   *
   * @param updates the updates to group
   * @param depth the depth at which to extract the nibble
   * @return map of nibble -> list of updates
   */
  private Map<Byte, List<UpdateEntry<V>>> groupUpdatesByNibble(
      final List<UpdateEntry<V>> updates, final int depth) {
    return updates.stream().collect(Collectors.groupingBy(entry -> entry.getNibble(depth)));
  }

  /**
   * Commits or hashes a node depending on whether we're committing to storage. If committing,
   * stores the node in the commit cache for later persistence. If not committing, just computes the
   * hash.
   *
   * @param node the node to commit or hash
   * @param location the storage location of the node
   * @param maybeCommitCache optional commit cache for storing nodes
   */
  private void commitOrHashNode(
      final Node<V> node, final Bytes location, final Optional<CommitCache> maybeCommitCache) {
    if (maybeCommitCache.isPresent()) {
      // Store in commit cache for later persistence
      node.accept(
          location,
          new CommitVisitor<>(
              (loc, hash, value) -> maybeCommitCache.get().store(loc, hash, value)));
    } else {
      // Just compute hash (triggers lazy computation if needed)
      Objects.requireNonNull(node.getHash());
    }
  }

  /**
   * Stores the root node and resets it to a lazy-loaded reference. This saves memory by not keeping
   * the full tree structure in memory.
   *
   * @param nodeUpdater the updater to persist the root node
   */
  private void storeAndResetRoot(final NodeUpdater nodeUpdater) {
    final Bytes32 rootHash = root.getHash();
    nodeUpdater.store(Bytes.EMPTY, rootHash, root.getEncodedBytes());

    // Reset root to lazy-loaded reference (or null if empty)
    this.root =
        rootHash.equals(EMPTY_TRIE_NODE_HASH)
            ? NullNode.instance()
            : new StoredNode<>(nodeFactory, Bytes.EMPTY, rootHash);
  }

  /**
   * Loads a node if it's a lazy reference (StoredNode), otherwise returns it as-is.
   *
   * @param node the node to load
   * @return the loaded node
   */
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

  /**
   * Represents a single update operation (put or remove).
   *
   * @param path the full nibble path to the key
   * @param value optional value (empty for removes, present for puts)
   * @param <V> the value type
   */
  private record UpdateEntry<V>(Bytes path, Optional<V> value) {
    /**
     * Gets the nibble at the specified index, or 0 if index is out of bounds.
     *
     * @param index the nibble index
     * @return the nibble value (0-15)
     */
    byte getNibble(final int index) {
      return index >= path.size() ? 0 : path.get(index);
    }
  }

  /**
   * Thread-safe wrapper for a BranchNode that allows concurrent child updates. Maintains a
   * synchronized list of pending child nodes.
   */
  private class BranchWrapper {
    private final BranchNode<V> originalBranch;
    private final List<Node<V>> pendingChildren;

    /**
     * Creates a wrapper for the given branch node.
     *
     * @param branch the branch node to wrap
     */
    BranchWrapper(final BranchNode<V> branch) {
      this.originalBranch = branch;
      this.pendingChildren = Collections.synchronizedList(new ArrayList<>(branch.getChildren()));
    }

    /**
     * Gets the list of pending child nodes.
     *
     * @return the synchronized list of children
     */
    List<Node<V>> getPendingChildren() {
      return pendingChildren;
    }

    /**
     * Sets a child node at the specified index. Thread-safe due to synchronized list.
     *
     * @param index the child index (0-15)
     * @param child the new child node
     */
    void setChild(final byte index, final Node<V> child) {
      pendingChildren.set(index, child);
    }

    /**
     * Applies all pending child updates to create a new branch node.
     *
     * @return the new branch node with updated children
     */
    Node<V> applyUpdates() {
      return originalBranch.replaceAllChildren(pendingChildren, true);
    }
  }

  /**
   * Thread-safe cache for node data during parallel commit operations. Accumulates all node updates
   * and flushes them to storage atomically.
   */
  private static class CommitCache {
    private final Map<Bytes, NodeData> cache = new ConcurrentHashMap<>();

    /**
     * Stores node data in the cache.
     *
     * @param location the storage location
     * @param hash the node hash
     * @param encodedBytes the encoded node bytes
     */
    void store(final Bytes location, final Bytes32 hash, final Bytes encodedBytes) {
      cache.put(location, new NodeData(hash, encodedBytes));
    }

    /**
     * Flushes all cached node data to the provided node updater.
     *
     * @param nodeUpdater the updater to persist nodes to storage
     */
    void flushTo(final NodeUpdater nodeUpdater) {
      cache.forEach(
          (location, nodeData) ->
              nodeUpdater.store(location, nodeData.hash, nodeData.encodedBytes));
    }

    /**
     * Simple data holder for node information.
     *
     * @param hash the node hash
     * @param encodedBytes the encoded node bytes
     */
    private record NodeData(Bytes32 hash, Bytes encodedBytes) {}
  }
}
