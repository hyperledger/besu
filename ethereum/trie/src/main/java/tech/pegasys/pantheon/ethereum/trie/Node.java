package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

interface Node<V> {

  Node<V> accept(PathNodeVisitor<V> visitor, BytesValue path);

  void accept(NodeVisitor<V> visitor);

  BytesValue getPath();

  Optional<V> getValue();

  BytesValue getRlp();

  BytesValue getRlpRef();

  Bytes32 getHash();

  Node<V> replacePath(BytesValue path);

  /** Marks the node as needing to be persisted */
  void markDirty();

  /** @return True if the node needs to be persisted. */
  boolean isDirty();

  String print();
}
