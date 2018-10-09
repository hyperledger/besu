package net.consensys.pantheon.ethereum.trie;

interface NodeVisitor<V> {

  void visit(ExtensionNode<V> extensionNode);

  void visit(BranchNode<V> branchNode);

  void visit(LeafNode<V> leafNode);

  void visit(NullNode<V> nullNode);
}
