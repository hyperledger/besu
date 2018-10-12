package net.consensys.pantheon.ethereum.trie;

import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

public interface NodeUpdater {
  void store(Bytes32 hash, BytesValue value);
}
