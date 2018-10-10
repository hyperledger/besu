package net.consensys.pantheon.ethereum.trie;

import net.consensys.pantheon.util.bytes.BytesValue;

interface PathNodeVisitor<V> {

  Node<V> visit(ExtensionNode<V> extensionNode, BytesValue path);

  Node<V> visit(BranchNode<V> branchNode, BytesValue path);

  Node<V> visit(LeafNode<V> leafNode, BytesValue path);

  Node<V> visit(NullNode<V> nullNode, BytesValue path);
}
