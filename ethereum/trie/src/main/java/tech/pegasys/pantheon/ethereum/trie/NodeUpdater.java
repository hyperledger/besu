package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public interface NodeUpdater {
  void store(Bytes32 hash, BytesValue value);
}
