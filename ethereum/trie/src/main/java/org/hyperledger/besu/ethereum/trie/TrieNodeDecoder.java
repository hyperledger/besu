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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TrieNodeDecoder {
  private static final StoredNodeFactory<Bytes> emptyNodeFactory =
      new StoredNodeFactory<>((h) -> Optional.empty(), Function.identity(), Function.identity());

  // Hide constructor for static utility class
  private TrieNodeDecoder() {}

  /**
   * Decode an rlp-encoded trie node
   *
   * @param rlp The rlp-encoded node
   * @return A {@code Node} representation of the rlp data
   */
  public static Node<Bytes> decode(final Bytes rlp) {
    return emptyNodeFactory.decode(rlp);
  }

  /**
   * Flattens this node and all of its inlined nodes and node references into a list.
   *
   * @param nodeRlp The bytes of the trie node to be decoded.
   * @return A list of nodes and node references embedded in the given rlp.
   */
  public static List<Node<Bytes>> decodeNodes(final Bytes nodeRlp) {
    Node<Bytes> node = decode(nodeRlp);
    List<Node<Bytes>> nodes = new ArrayList<>();
    nodes.add(node);

    final List<Node<Bytes>> toProcess = new ArrayList<>();
    toProcess.addAll(node.getChildren());
    while (!toProcess.isEmpty()) {
      final Node<Bytes> currentNode = toProcess.remove(0);
      if (Objects.equals(NullNode.instance(), currentNode)) {
        // Skip null nodes
        continue;
      }
      nodes.add(currentNode);

      if (!currentNode.isReferencedByHash()) {
        // If current node is inlined, that means we can process its children
        toProcess.addAll(currentNode.getChildren());
      }
    }

    return nodes;
  }

  /**
   * Walks the trie in a bread-first manner, returning the list of nodes encountered in order. If
   * any nodes are missing from the nodeLoader, those nodes are just skipped.
   *
   * @param nodeLoader The NodeLoader for looking up nodes by hash
   * @param rootHash The hash of the root node
   * @param maxDepth The maximum depth to traverse to. A value of zero will traverse the root node
   *     only.
   * @return A stream non-null nodes in the breadth-first traversal order.
   */
  public static Stream<Node<Bytes>> breadthFirstDecoder(
      final NodeLoader nodeLoader, final Bytes32 rootHash, final int maxDepth) {
    checkArgument(maxDepth >= 0);
    return Streams.stream(new BreadthFirstIterator(nodeLoader, rootHash, maxDepth));
  }

  /**
   * Walks the trie in a bread-first manner, returning the list of nodes encountered in order. If
   * any nodes are missing from the nodeLoader, those nodes are just skipped.
   *
   * @param nodeLoader The NodeLoader for looking up nodes by hash
   * @param rootHash The hash of the root node
   * @return A stream non-null nodes in the breadth-first traversal order.
   */
  public static Stream<Node<Bytes>> breadthFirstDecoder(
      final NodeLoader nodeLoader, final Bytes32 rootHash) {
    return breadthFirstDecoder(nodeLoader, rootHash, Integer.MAX_VALUE);
  }

  private static class BreadthFirstIterator implements Iterator<Node<Bytes>> {

    private final int maxDepth;
    private final StoredNodeFactory<Bytes> nodeFactory;

    private int currentDepth = 0;
    private final List<Node<Bytes>> currentNodes = new ArrayList<>();
    private final List<Node<Bytes>> nextNodes = new ArrayList<>();

    BreadthFirstIterator(final NodeLoader nodeLoader, final Bytes32 rootHash, final int maxDepth) {
      this.maxDepth = maxDepth;
      this.nodeFactory =
          new StoredNodeFactory<>(nodeLoader, Function.identity(), Function.identity());

      nodeLoader.getNode(rootHash).map(TrieNodeDecoder::decode).ifPresent(currentNodes::add);
    }

    public static BreadthFirstIterator create(
        final NodeLoader nodeLoader, final Bytes32 rootHash, final int maxDepth) {
      return new BreadthFirstIterator(nodeLoader, rootHash, maxDepth);
    }

    @Override
    public boolean hasNext() {
      return !currentNodes.isEmpty() && currentDepth <= maxDepth;
    }

    @Override
    public Node<Bytes> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      final Node<Bytes> nextNode = currentNodes.remove(0);

      final List<Node<Bytes>> children = new ArrayList<>();
      children.addAll(nextNode.getChildren());
      while (!children.isEmpty()) {
        Node<Bytes> child = children.remove(0);
        if (Objects.equals(child, NullNode.instance())) {
          // Ignore null nodes
          continue;
        }
        if (child.isReferencedByHash()) {
          // Retrieve hash-referenced child
          final Optional<Node<Bytes>> maybeChildNode = nodeFactory.retrieve(child.getHash());
          if (!maybeChildNode.isPresent()) {
            continue;
          }
          child = maybeChildNode.get();
        }
        nextNodes.add(child);
      }

      // Set up next level
      if (currentNodes.isEmpty()) {
        currentDepth += 1;
        currentNodes.addAll(nextNodes);
        nextNodes.clear();
      }

      return nextNode;
    }
  }
}
