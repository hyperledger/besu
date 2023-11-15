package org.hyperledger.besu.ethereum.trie;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;

import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface TrieKeyValueGenerator {

  Map<? extends Bytes, ? extends Bytes> generateKeyValuesForAccount(
      Address address, Bytes accountRlp, long nonce, Wei balance, Bytes32 codeHash, Bytes code);

  Map<? extends Bytes, ? extends Bytes> generateKeyValuesForStorage(
      Address address, StorageSlotKey storageKey, Bytes value);
}
