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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;
import org.hyperledger.besu.ethereum.trie.NullNode;
import org.hyperledger.besu.ethereum.trie.StoredNode;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

/**
 * Merkle Patricia trie built from 32-byte keys inserted in strictly ascending order. Only its right
 * edge stays in memory: a subtree is built once no later key can reach it, handed to the sink at
 * its nibble path when referenced by hash, then collapsed to a {@link StoredNode} hash stub. It
 * also takes subtrees known only by their reference, and partial builds of a key range, to assemble
 * range proofs and partitioned builds.
 */
public final class AscendingCollapsePatriciaTrie {

  private static final int KEY_NIBBLES = 64;
  private static final NodeUpdater NO_SINK = (location, hash, node) -> {};

  /**
   * What the build of a key range leaves, to insert into the trie of the whole key space.
   *
   * @param path the nibble path of a key in the range
   * @param depth the depth of the subtree root, the key length for a leaf
   * @param value the value of a leaf, null for a subtree
   * @param node the subtree root, null for a leaf
   */
  public record Partition(Bytes path, int depth, Bytes value, Node<Bytes> node) {}

  private final DefaultNodeFactory<Bytes> nodeFactory =
      new DefaultNodeFactory<>(Function.identity());
  private final NodeUpdater sink;
  private final int[] frameDepth = new int[KEY_NIBBLES];

  @SuppressWarnings({"unchecked", "rawtypes"})
  private final Node<Bytes>[][] frameChildren = new Node[KEY_NIBBLES][16];

  private int frames;
  private long insertCount;

  // the right-most element, not yet attached to its parent: a leaf, a subtree or a sealed subtree
  private Bytes lastPath;
  private Bytes leafValue;
  private Node<Bytes> subtree;
  private int subtreeDepth;
  private boolean sealed;

  /** Creates an empty trie that only computes its root. */
  public AscendingCollapsePatriciaTrie() {
    this(NO_SINK);
  }

  /**
   * Creates an empty trie.
   *
   * @param sink receives each node referenced by hash at its nibble path, children before parents,
   *     and the root
   */
  public AscendingCollapsePatriciaTrie(final NodeUpdater sink) {
    this.sink = sink;
  }

  /**
   * Inserts a leaf.
   *
   * @param key the 32-byte key, greater than every key inserted before
   * @param value the leaf value
   */
  public void insert(final Bytes key, final Bytes value) {
    checkArgument(key.size() == 32, "keys must have 32 bytes");
    checkArgument(!value.isEmpty(), "Patricia trie rejects empty values");
    put(CompactEncoding.bytesToPath(key), KEY_NIBBLES, value, null, false);
    insertCount++;
  }

  /** Returns the number of leaves inserted. */
  public long insertCount() {
    return insertCount;
  }

  /**
   * Inserts a subtree known only by its reference.
   *
   * @param location the nibble path of the subtree root
   * @param ref the reference its parent branch holds: a hash reference or an inlined node
   */
  public void insertSealed(final Bytes location, final Bytes ref) {
    final Node<Bytes> node =
        isHashRef(ref)
            ? new StoredNode<>(nodeFactory, location, Bytes32.wrap(ref.slice(1)))
            : TrieNodeDecoder.decode(location, ref);
    put(padded(location), location.size(), null, node, true);
  }

  /** Inserts what {@link #finishAsPartition()} left on a key range. */
  public void insertPartition(final Partition partition) {
    put(partition.path(), partition.depth(), partition.value(), partition.node(), false);
  }

  /**
   * Finishes the build of a key range and returns what is left to insert into the whole trie, or
   * null if nothing was inserted. The nodes below it have been handed to the sink.
   */
  public Partition finishAsPartition() {
    if (lastPath == null) {
      return null;
    }
    popFramesDeeperThan(-1);
    if (sealed) {
      throw new IllegalStateException("a partition cannot end with a sealed subtree");
    }
    return new Partition(
        lastPath, leafValue != null ? KEY_NIBBLES : subtreeDepth, leafValue, subtree);
  }

  /**
   * Finishes the trie and returns its root hash. The root node is always handed to the sink.
   *
   * @return the root hash
   */
  public Bytes32 rootHash() {
    if (lastPath == null) {
      return MerkleTrie.EMPTY_TRIE_NODE_HASH;
    }
    popFramesDeeperThan(-1);
    final Node<Bytes> root;
    if (leafValue != null) {
      root = nodeFactory.createLeaf(lastPath, leafValue);
    } else if (subtreeDepth > 0) {
      if (sealed) {
        throw new IllegalStateException("a sealed subtree needs a parent branch");
      }
      root = nodeFactory.createExtension(lastPath.slice(0, subtreeDepth), subtree);
    } else if (sealed || subtree instanceof StoredNode) {
      // the root itself, known by reference or already handed at the root location
      return subtree.getHash();
    } else {
      root = subtree;
    }
    final Bytes32 hash = root.getHash();
    sink.store(Bytes.EMPTY, hash, root.getEncodedBytes());
    return hash;
  }

  private void put(
      final Bytes path,
      final int depth,
      final Bytes value,
      final Node<Bytes> node,
      final boolean isSealed) {
    if (lastPath != null) {
      if (lastPath.compareTo(path) >= 0) {
        throw new IllegalArgumentException("keys must be inserted in strictly ascending order");
      }
      final int split = lastPath.commonPrefixLength(path);
      if (split >= depth || (leafValue == null && split >= subtreeDepth)) {
        throw new IllegalArgumentException("overlapping subtrees");
      }
      popFramesDeeperThan(split);
      attachPending(split);
    }
    lastPath = path;
    leafValue = value;
    subtree = node;
    subtreeDepth = depth;
    sealed = isSealed;
  }

  private void attachPending(final int depth) {
    if (frames == 0 || frameDepth[frames - 1] != depth) {
      frameDepth[frames++] = depth;
    }
    frameChildren[frames - 1][lastPath.get(depth)] = pendingNode(depth + 1);
  }

  // builds the branches no later key can reach, the deepest first
  private void popFramesDeeperThan(final int depth) {
    while (frames > 0 && frameDepth[frames - 1] > depth) {
      final int d = frameDepth[frames - 1];
      final Node<Bytes>[] children = frameChildren[frames - 1];
      children[lastPath.get(d)] = pendingNode(d + 1);
      final Node<Bytes>[] branch = Arrays.copyOf(children, 16);
      for (int i = 0; i < 16; i++) {
        if (branch[i] == null) {
          branch[i] = NullNode.instance();
        }
      }
      Arrays.fill(children, null);
      frames--;
      subtree = collapse(nodeFactory.createBranch(Arrays.asList(branch), Optional.empty()), d);
      subtreeDepth = d;
      leafValue = null;
      sealed = false;
    }
  }

  // the pending element as the child attached at the given depth
  private Node<Bytes> pendingNode(final int depth) {
    if (leafValue != null) {
      return collapse(nodeFactory.createLeaf(lastPath.slice(depth), leafValue), depth);
    }
    if (subtreeDepth == depth) {
      return subtree;
    }
    if (sealed) {
      throw new IllegalStateException("a sealed subtree must hang under a branch");
    }
    return collapse(
        nodeFactory.createExtension(lastPath.slice(depth, subtreeDepth - depth), subtree), depth);
  }

  // hands a node referenced by hash to the sink and keeps only its hash
  private Node<Bytes> collapse(final Node<Bytes> node, final int depth) {
    if (!node.isReferencedByHash()) {
      return node;
    }
    final Bytes location = lastPath.slice(0, depth);
    final Bytes32 hash = node.getHash();
    sink.store(location, hash, node.getEncodedBytes());
    return new StoredNode<>(nodeFactory, location, hash);
  }

  // the nibble path of the lowest key under a location, as a leaf path
  private static Bytes padded(final Bytes location) {
    checkArgument(location.size() < KEY_NIBBLES, "a sealed subtree is not a leaf");
    final MutableBytes path = MutableBytes.create(KEY_NIBBLES + 1);
    path.set(0, location);
    path.set(KEY_NIBBLES, CompactEncoding.LEAF_TERMINATOR);
    return path;
  }

  private static boolean isHashRef(final Bytes ref) {
    return ref.size() == 33 && ref.get(0) == (byte) 0xa0;
  }
}
