package net.consensys.pantheon.ethereum.trie;

import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

class DefaultNodeFactory<V> implements NodeFactory<V> {
  @SuppressWarnings("rawtypes")
  private static final Node NULL_NODE = NullNode.instance();

  private final Function<V, BytesValue> valueSerializer;

  DefaultNodeFactory(final Function<V, BytesValue> valueSerializer) {
    this.valueSerializer = valueSerializer;
  }

  @Override
  public Node<V> createExtension(final BytesValue path, final Node<V> child) {
    return new ExtensionNode<>(path, child, this);
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
    return new BranchNode<>(children, value, this, valueSerializer);
  }

  @Override
  public Node<V> createLeaf(final BytesValue path, final V value) {
    return new LeafNode<>(path, value, this, valueSerializer);
  }
}
