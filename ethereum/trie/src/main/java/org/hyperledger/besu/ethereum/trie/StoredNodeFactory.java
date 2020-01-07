/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.trie;

import static java.lang.String.format;

import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class StoredNodeFactory<V> implements NodeFactory<V> {
  @SuppressWarnings("rawtypes")
  private static final NullNode NULL_NODE = NullNode.instance();

  private final NodeLoader nodeLoader;
  private final Function<V, Bytes> valueSerializer;
  private final Function<Bytes, V> valueDeserializer;

  StoredNodeFactory(
      final NodeLoader nodeLoader,
      final Function<V, Bytes> valueSerializer,
      final Function<Bytes, V> valueDeserializer) {
    this.nodeLoader = nodeLoader;
    this.valueSerializer = valueSerializer;
    this.valueDeserializer = valueDeserializer;
  }

  @Override
  public Node<V> createExtension(final Bytes path, final Node<V> child) {
    return handleNewNode(new ExtensionNode<>(path, child, this));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node<V> createBranch(
      final byte leftIndex, final Node<V> left, final byte rightIndex, final Node<V> right) {
    assert (leftIndex <= BranchNode.RADIX);
    assert (rightIndex <= BranchNode.RADIX);
    assert (leftIndex != rightIndex);

    final ArrayList<Node<V>> children =
        new ArrayList<>(Collections.nCopies(BranchNode.RADIX, (Node<V>) NULL_NODE));

    if (leftIndex == BranchNode.RADIX) {
      children.set(rightIndex, right);
      return createBranch(children, left.getValue());
    } else if (rightIndex == BranchNode.RADIX) {
      children.set(leftIndex, left);
      return createBranch(children, right.getValue());
    } else {
      children.set(leftIndex, left);
      children.set(rightIndex, right);
      return createBranch(children, Optional.empty());
    }
  }

  @Override
  public Node<V> createBranch(final ArrayList<Node<V>> children, final Optional<V> value) {
    return handleNewNode(new BranchNode<>(children, value, this, valueSerializer));
  }

  @Override
  public Node<V> createLeaf(final Bytes path, final V value) {
    return handleNewNode(new LeafNode<>(path, value, this, valueSerializer));
  }

  private Node<V> handleNewNode(final Node<V> node) {
    node.markDirty();
    return node;
  }

  public Optional<Node<V>> retrieve(final Bytes32 hash) throws MerkleTrieException {
    return nodeLoader
        .getNode(hash)
        .map(
            rlp -> {
              final Node<V> node = decode(rlp, () -> format("Invalid RLP value for hash %s", hash));
              // recalculating the node.hash() is expensive, so we only do this as an assertion
              assert (hash.equals(node.getHash()))
                  : "Node hash " + node.getHash() + " not equal to expected " + hash;
              return node;
            });
  }

  public Node<V> decode(final Bytes rlp) {
    return decode(rlp, () -> String.format("Failed to decode value %s", rlp.toString()));
  }

  private Node<V> decode(final Bytes rlp, final Supplier<String> errMessage)
      throws MerkleTrieException {
    try {
      return decode(RLP.input(rlp), errMessage);
    } catch (final RLPException ex) {
      throw new MerkleTrieException(errMessage.get(), ex);
    }
  }

  private Node<V> decode(final RLPInput nodeRLPs, final Supplier<String> errMessage) {
    final int nodesCount = nodeRLPs.enterList();
    try {
      switch (nodesCount) {
        case 1:
          return decodeNull(nodeRLPs, errMessage);

        case 2:
          final Bytes encodedPath = nodeRLPs.readBytes();
          final Bytes path;
          try {
            path = CompactEncoding.decode(encodedPath);
          } catch (final IllegalArgumentException ex) {
            throw new MerkleTrieException(errMessage.get() + ": invalid path " + encodedPath, ex);
          }

          final int size = path.size();
          if (size > 0 && path.get(size - 1) == CompactEncoding.LEAF_TERMINATOR) {
            return decodeLeaf(path, nodeRLPs, errMessage);
          } else {
            return decodeExtension(path, nodeRLPs, errMessage);
          }

        case (BranchNode.RADIX + 1):
          return decodeBranch(nodeRLPs, errMessage);

        default:
          throw new MerkleTrieException(
              errMessage.get() + format(": invalid list size %s", nodesCount));
      }
    } finally {
      nodeRLPs.leaveList();
    }
  }

  private Node<V> decodeExtension(
      final Bytes path, final RLPInput valueRlp, final Supplier<String> errMessage) {
    final RLPInput childRlp = valueRlp.readAsRlp();
    if (childRlp.nextIsList()) {
      final Node<V> childNode = decode(childRlp, errMessage);
      return new ExtensionNode<>(path, childNode, this);
    } else {
      final Bytes32 childHash = childRlp.readBytes32();
      final StoredNode<V> childNode = new StoredNode<>(this, childHash);
      return new ExtensionNode<>(path, childNode, this);
    }
  }

  @SuppressWarnings("unchecked")
  private BranchNode<V> decodeBranch(final RLPInput nodeRLPs, final Supplier<String> errMessage) {
    final ArrayList<Node<V>> children = new ArrayList<>(BranchNode.RADIX);
    for (int i = 0; i < BranchNode.RADIX; ++i) {
      if (nodeRLPs.nextIsNull()) {
        nodeRLPs.skipNext();
        children.add(NULL_NODE);
      } else if (nodeRLPs.nextIsList()) {
        final Node<V> child = decode(nodeRLPs, errMessage);
        children.add(child);
      } else {
        final Bytes32 childHash = nodeRLPs.readBytes32();
        children.add(new StoredNode<>(this, childHash));
      }
    }

    final Optional<V> value;
    if (nodeRLPs.nextIsNull()) {
      nodeRLPs.skipNext();
      value = Optional.empty();
    } else {
      value = Optional.of(decodeValue(nodeRLPs, errMessage));
    }

    return new BranchNode<>(children, value, this, valueSerializer);
  }

  private LeafNode<V> decodeLeaf(
      final Bytes path, final RLPInput valueRlp, final Supplier<String> errMessage) {
    if (valueRlp.nextIsNull()) {
      throw new MerkleTrieException(errMessage.get() + ": leaf has null value");
    }
    final V value = decodeValue(valueRlp, errMessage);
    return new LeafNode<>(path, value, this, valueSerializer);
  }

  @SuppressWarnings("unchecked")
  private NullNode<V> decodeNull(final RLPInput nodeRLPs, final Supplier<String> errMessage) {
    if (!nodeRLPs.nextIsNull()) {
      throw new MerkleTrieException(errMessage.get() + ": list size 1 but not null");
    }
    nodeRLPs.skipNext();
    return NULL_NODE;
  }

  private V decodeValue(final RLPInput valueRlp, final Supplier<String> errMessage) {
    final Bytes bytes;
    try {
      bytes = valueRlp.readBytes();
    } catch (final RLPException ex) {
      throw new MerkleTrieException(
          errMessage.get() + ": failed decoding value rlp " + valueRlp, ex);
    }
    return deserializeValue(errMessage, bytes);
  }

  private V deserializeValue(final Supplier<String> errMessage, final Bytes bytes) {
    final V value;
    try {
      value = valueDeserializer.apply(bytes);
    } catch (final IllegalArgumentException ex) {
      throw new MerkleTrieException(errMessage.get() + ": failed deserializing value " + bytes, ex);
    }
    return value;
  }
}
