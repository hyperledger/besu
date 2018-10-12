package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public interface NodeLoader {
  Optional<BytesValue> getNode(Bytes32 hash);
}
