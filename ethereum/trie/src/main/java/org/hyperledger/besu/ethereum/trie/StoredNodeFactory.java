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
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class StoredNodeFactory implements NodeFactory {
  @SuppressWarnings("rawtypes")
  private static final NullNode NULL_NODE = NullNode.instance();

  private final NodeLoader nodeLoader;

  StoredNodeFactory(final NodeLoader nodeLoader) {
    this.nodeLoader = nodeLoader;
  }

  @Override
  public Node createExtension(final Bytes path, final Node child) {
    return handleNewNode(new ExtensionNode(path, child, this));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Node createBranch(
      final byte leftIndex, final Node left, final byte rightIndex, final Node right) {
    assert (leftIndex <= BranchNode.RADIX);
    assert (rightIndex <= BranchNode.RADIX);
    assert (leftIndex != rightIndex);

    final ArrayList<Node> children =
        new ArrayList<>(Collections.nCopies(BranchNode.RADIX, NULL_NODE));

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
  public Node createBranch(final ArrayList<Node> children, final Optional<Bytes> value) {
    return handleNewNode(new BranchNode(children, value, this));
  }

  @Override
  public Node createLeaf(final Bytes path, final Bytes value) {
    return handleNewNode(new LeafNode(path, value, this));
  }

  private Node handleNewNode(final Node node) {
    node.markDirty();
    return node;
  }

  public Optional<Node> retrieve(final Bytes32 hash) throws MerkleTrieException {
    return nodeLoader
        .getNode(hash)
        .map(
            rlp -> {
              final Node node = decode(rlp, () -> format("Invalid RLP value for hash %s", hash));
              // recalculating the node.hash() is expensive, so we only do this as an assertion
              assert (hash.equals(node.getHash()))
                  : "Node hash " + node.getHash() + " not equal to expected " + hash;
              return node;
            });
  }

  public Node decode(final Bytes rlp) {
    return decode(rlp, () -> String.format("Failed to decode value %s", rlp.toString()));
  }

  private Node decode(final Bytes rlp, final Supplier<String> errMessage)
      throws MerkleTrieException {
    try {
      return decode(RLP.input(rlp), errMessage);
    } catch (final RLPException ex) {
      throw new MerkleTrieException(errMessage.get(), ex);
    }
  }

  private Node decode(final RLPInput nodeRLPs, final Supplier<String> errMessage) {
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

  private Node decodeExtension(
      final Bytes path, final RLPInput valueRlp, final Supplier<String> errMessage) {
    final RLPInput childRlp = valueRlp.readAsRlp();
    if (childRlp.nextIsList()) {
      final Node childNode = decode(childRlp, errMessage);
      return new ExtensionNode(path, childNode, this);
    } else {
      final Bytes32 childHash = childRlp.readBytes32();
      final StoredNode childNode = new StoredNode(this, childHash);
      return new ExtensionNode(path, childNode, this);
    }
  }

  private BranchNode decodeBranch(final RLPInput nodeRLPs, final Supplier<String> errMessage) {
    final ArrayList<Node> children = new ArrayList<>(BranchNode.RADIX);
    for (int i = 0; i < BranchNode.RADIX; ++i) {
      if (nodeRLPs.nextIsNull()) {
        nodeRLPs.skipNext();
        children.add(NULL_NODE);
      } else if (nodeRLPs.nextIsList()) {
        final Node child = decode(nodeRLPs, errMessage);
        children.add(child);
      } else {
        final Bytes32 childHash = nodeRLPs.readBytes32();
        children.add(new StoredNode(this, childHash));
      }
    }

    final Optional<Bytes> value;
    if (nodeRLPs.nextIsNull()) {
      nodeRLPs.skipNext();
      value = Optional.empty();
    } else {
      value = Optional.of(decodeValue(nodeRLPs, errMessage));
    }

    return new BranchNode(children, value, this);
  }

  private LeafNode decodeLeaf(
      final Bytes path, final RLPInput valueRlp, final Supplier<String> errMessage) {
    if (valueRlp.nextIsNull()) {
      throw new MerkleTrieException(errMessage.get() + ": leaf has null value");
    }
    final Bytes value = decodeValue(valueRlp, errMessage);
    return new LeafNode(path, value, this);
  }

  @SuppressWarnings("unchecked")
  private NullNode decodeNull(final RLPInput nodeRLPs, final Supplier<String> errMessage) {
    if (!nodeRLPs.nextIsNull()) {
      throw new MerkleTrieException(errMessage.get() + ": list size 1 but not null");
    }
    nodeRLPs.skipNext();
    return NULL_NODE;
  }

  private Bytes decodeValue(final RLPInput valueRlp, final Supplier<String> errMessage) {
    final Bytes bytes;
    try {
      bytes = valueRlp.readBytes();
    } catch (final RLPException ex) {
      throw new MerkleTrieException(
          errMessage.get() + ": failed decoding value rlp " + valueRlp, ex);
    }
    return bytes;
  }
}
