package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface FallbackTrieNodeFinder {

  Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash);

  Optional<Bytes> getAccountStorageTrieNode(Hash accountHash, Bytes location, Bytes32 nodeHash);
}
