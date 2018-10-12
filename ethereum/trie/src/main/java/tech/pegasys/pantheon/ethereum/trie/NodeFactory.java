package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Optional;

interface NodeFactory<V> {

  Node<V> createExtension(BytesValue path, Node<V> child);

  Node<V> createBranch(byte leftIndex, Node<V> left, byte rightIndex, Node<V> right);

  Node<V> createBranch(ArrayList<Node<V>> newChildren, Optional<V> value);

  Node<V> createLeaf(BytesValue path, V value);
}
