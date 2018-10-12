package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.BytesValue;

class GetVisitor<V> implements PathNodeVisitor<V> {
  private final Node<V> NULL_NODE_RESULT = NullNode.instance();

  @Override
  public Node<V> visit(final ExtensionNode<V> extensionNode, final BytesValue path) {
    final BytesValue extensionPath = extensionNode.getPath();
    final int commonPathLength = extensionPath.commonPrefixLength(path);
    assert commonPathLength < path.size()
        : "Visiting path doesn't end with a non-matching terminator";

    if (commonPathLength < extensionPath.size()) {
      // path diverges before the end of the extension, so it cannot match
      return NULL_NODE_RESULT;
    }

    return extensionNode.getChild().accept(this, path.slice(commonPathLength));
  }

  @Override
  public Node<V> visit(final BranchNode<V> branchNode, final BytesValue path) {
    assert path.size() > 0 : "Visiting path doesn't end with a non-matching terminator";

    final byte childIndex = path.get(0);
    if (childIndex == CompactEncoding.LEAF_TERMINATOR) {
      return branchNode;
    }

    return branchNode.child(childIndex).accept(this, path.slice(1));
  }

  @Override
  public Node<V> visit(final LeafNode<V> leafNode, final BytesValue path) {
    final BytesValue leafPath = leafNode.getPath();
    if (leafPath.commonPrefixLength(path) != leafPath.size()) {
      return NULL_NODE_RESULT;
    }
    return leafNode;
  }

  @Override
  public Node<V> visit(final NullNode<V> nullNode, final BytesValue path) {
    return NULL_NODE_RESULT;
  }
}
