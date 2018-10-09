package net.consensys.pantheon.ethereum.trie;

import net.consensys.pantheon.util.bytes.BytesValue;

class CommitVisitor<V> implements NodeVisitor<V> {

  private final NodeUpdater nodeUpdater;

  public CommitVisitor(final NodeUpdater nodeUpdater) {
    this.nodeUpdater = nodeUpdater;
  }

  @Override
  public void visit(final ExtensionNode<V> extensionNode) {
    if (!extensionNode.isDirty()) {
      return;
    }

    final Node<V> child = extensionNode.getChild();
    if (child.isDirty()) {
      child.accept(this);
    }

    maybeStoreNode(extensionNode);
  }

  @Override
  public void visit(final BranchNode<V> branchNode) {
    if (!branchNode.isDirty()) {
      return;
    }

    for (byte i = 0; i < BranchNode.RADIX; ++i) {
      final Node<V> child = branchNode.child(i);
      if (child.isDirty()) {
        child.accept(this);
      }
    }

    maybeStoreNode(branchNode);
  }

  @Override
  public void visit(final LeafNode<V> leafNode) {
    if (!leafNode.isDirty()) {
      return;
    }

    maybeStoreNode(leafNode);
  }

  @Override
  public void visit(final NullNode<V> nullNode) {}

  private void maybeStoreNode(final Node<V> node) {
    final BytesValue nodeRLP = node.getRlp();
    if (nodeRLP.size() >= 32) {
      this.nodeUpdater.store(node.getHash(), nodeRLP);
    }
  }
}
