package net.consensys.pantheon.ethereum.worldstate;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

public interface WorldStateStorage {

  Optional<BytesValue> getCode(Hash codeHash);

  Optional<BytesValue> getAccountStateTrieNode(Bytes32 nodeHash);

  Optional<BytesValue> getAccountStorageTrieNode(Bytes32 nodeHash);

  Updater updater();

  interface Updater {

    void putCode(BytesValue code);

    void putAccountStateTrieNode(Bytes32 nodeHash, BytesValue node);

    void putAccountStorageTrieNode(Bytes32 nodeHash, BytesValue node);

    void commit();

    void rollback();
  }
}
