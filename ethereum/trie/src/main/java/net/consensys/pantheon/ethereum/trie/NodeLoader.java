package net.consensys.pantheon.ethereum.trie;

import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public interface NodeLoader {
  Optional<BytesValue> getNode(Bytes32 hash);
}
