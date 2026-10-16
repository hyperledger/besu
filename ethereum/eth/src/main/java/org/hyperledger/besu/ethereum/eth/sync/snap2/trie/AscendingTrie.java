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
package org.hyperledger.besu.ethereum.eth.sync.snap2.trie;

import static org.hyperledger.besu.ethereum.eth.sync.snap2.trie.NodeEncoding.commonPrefix;
import static org.hyperledger.besu.ethereum.eth.sync.snap2.trie.NodeEncoding.nibble;

import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.NodeUpdater;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Builds a trie from keys added in increasing order, keeping only its right edge in memory. It also
 * takes built or sealed subtrees, to assemble partitioned builds and range proofs.
 */
final class AscendingTrie {

  /** What the pending item is: nothing yet, a leaf, a branch, or a subtree known by reference. */
  enum Kind {
    NONE,
    LEAF,
    BRANCH,
    SEALED
  }

  private final NodeUpdater sink;
  private final int[] frameDepth = new int[64];
  private final Bytes[][] frameChildren = new Bytes[64][];
  private int frames;

  // pending item: the right-most element, not yet attached to its parent
  private byte[] lastKey;
  private Kind kind = Kind.NONE;
  private Bytes leafValue;
  private int itemDepth;
  private Bytes itemRlp;
  private Bytes itemRef;

  /**
   * Creates an empty trie.
   *
   * @param sink receives each node referenced by hash, children before parents, at its nibble path,
   *     and the root; or null to only compute the root
   */
  public AscendingTrie(final NodeUpdater sink) {
    this.sink = sink;
    for (int i = 0; i < frameChildren.length; i++) {
      frameChildren[i] = new Bytes[16];
    }
  }

  /** Item left after building a partition, to be added to the trie of the whole key space. */
  public record Item(Kind kind, Bytes32 key, int depth, Bytes value, Bytes rlp, Bytes ref) {}

  /**
   * Adds a leaf.
   *
   * @param key the 32-byte key, greater than every key added before
   * @param value the leaf value, already RLP encoded
   */
  public void addLeaf(final Bytes32 key, final Bytes value) {
    insert(key.toArrayUnsafe(), Kind.LEAF, 64, value, null, null);
  }

  /**
   * Adds a sealed subtree known only by its reference.
   *
   * @param location the nibble path of the subtree root
   * @param ref the reference held by the parent branch, a hash reference or an inlined node
   */
  public void addSealed(final Bytes location, final Bytes ref) {
    insert(NodeEncoding.padLocation(location), Kind.SEALED, location.size(), null, null, ref);
  }

  /** Adds an item produced by {@link #finishAsItem()} on a partition of the key space. */
  public void addItem(final Item item) {
    if (item.kind() == Kind.LEAF) {
      addLeaf(item.key(), item.value());
    } else {
      insert(item.key().toArrayUnsafe(), Kind.BRANCH, item.depth(), null, item.rlp(), item.ref());
    }
  }

  /**
   * Finishes the trie and returns its root hash. The root node is always emitted to the sink.
   *
   * @return the root hash
   */
  public Bytes32 rootHash() {
    if (kind == Kind.NONE) {
      return MerkleTrie.EMPTY_TRIE_NODE_HASH;
    }
    popFramesDeeperThan(-1);
    return switch (kind) {
      case LEAF -> emitRoot(NodeEncoding.leaf(lastKey, 0, leafValue));
      case BRANCH -> {
        if (itemDepth > 0) {
          yield emitRoot(NodeEncoding.extension(lastKey, 0, itemDepth, itemRef));
        }
        // a hashed branch at depth 0 was already emitted at the root location
        yield NodeEncoding.isHashRef(itemRef) ? NodeEncoding.refHash(itemRef) : emitRoot(itemRlp);
      }
      default -> {
        if (itemDepth != 0) {
          throw new IllegalStateException("a sealed subtree needs a parent branch");
        }
        yield NodeEncoding.refHash(itemRef);
      }
    };
  }

  /**
   * Finishes a partition of the key space and returns what is left to attach to its parent, or null
   * if nothing was added. Nodes below the returned item have been emitted.
   */
  public Item finishAsItem() {
    if (kind == Kind.NONE) {
      return null;
    }
    popFramesDeeperThan(-1);
    if (kind == Kind.SEALED) {
      throw new IllegalStateException("a partition cannot end with a sealed subtree");
    }
    return new Item(
        kind,
        Bytes32.wrap(lastKey),
        kind == Kind.LEAF ? 64 : itemDepth,
        leafValue,
        itemRlp,
        itemRef);
  }

  private void insert(
      final byte[] key,
      final Kind newKind,
      final int newDepth,
      final Bytes value,
      final Bytes rlp,
      final Bytes ref) {
    if (kind != Kind.NONE) {
      if (Arrays.compareUnsigned(lastKey, key) >= 0) {
        throw new IllegalArgumentException("keys must be added in strictly increasing order");
      }
      final int split = commonPrefix(lastKey, key);
      if (split >= newDepth || (kind != Kind.LEAF && split >= itemDepth)) {
        throw new IllegalArgumentException("overlapping subtrees");
      }
      popFramesDeeperThan(split);
      attachPending(split);
    }
    lastKey = key;
    kind = newKind;
    itemDepth = newDepth;
    leafValue = value;
    itemRlp = rlp;
    itemRef = ref;
  }

  private void attachPending(final int depth) {
    if (frames == 0 || frameDepth[frames - 1] != depth) {
      frameDepth[frames++] = depth;
    }
    frameChildren[frames - 1][nibble(lastKey, depth)] = pendingRef(depth + 1);
  }

  private void popFramesDeeperThan(final int depth) {
    while (frames > 0 && frameDepth[frames - 1] > depth) {
      final int d = frameDepth[frames - 1];
      final Bytes[] children = frameChildren[frames - 1];
      children[nibble(lastKey, d)] = pendingRef(d + 1);
      final Bytes rlp = NodeEncoding.branch(children);
      Arrays.fill(children, null);
      frames--;
      itemRef = ref(rlp, d);
      itemRlp = rlp;
      itemDepth = d;
      leafValue = null;
      kind = Kind.BRANCH;
    }
  }

  // reference of the pending item once attached at the given depth
  private Bytes pendingRef(final int depth) {
    return switch (kind) {
      case LEAF -> ref(NodeEncoding.leaf(lastKey, depth, leafValue), depth);
      case BRANCH ->
          itemDepth == depth
              ? itemRef
              : ref(NodeEncoding.extension(lastKey, depth, itemDepth, itemRef), depth);
      default -> {
        if (itemDepth != depth) {
          throw new IllegalStateException("a sealed subtree must hang under a branch");
        }
        yield itemRef;
      }
    };
  }

  private Bytes ref(final Bytes rlp, final int depth) {
    if (rlp.size() < 32) {
      return rlp;
    }
    final Bytes32 hash = NodeEncoding.keccak(rlp);
    if (sink != null) {
      sink.store(NodeEncoding.location(lastKey, depth), hash, rlp);
    }
    return NodeEncoding.hashRef(hash);
  }

  private Bytes32 emitRoot(final Bytes rlp) {
    final Bytes32 hash = NodeEncoding.keccak(rlp);
    if (sink != null) {
      sink.store(Bytes.EMPTY, hash, rlp);
    }
    return hash;
  }
}
