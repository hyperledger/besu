package org.hyperledger.besu.ethereum.verkletrie;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.verkle.adapter.TrieKeyBatchAdapter;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.Hasher;
import org.hyperledger.besu.ethereum.trie.verkle.hasher.PedersenHasher;
import org.hyperledger.besu.ethereum.trie.verkle.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TrieKeyPreloader {

  private final TrieKeyBatchAdapter trieKeyAdapter;

  private final Hasher hasher;

  public TrieKeyPreloader() {
    this.hasher = new PedersenHasher();
    trieKeyAdapter = new TrieKeyBatchAdapter(hasher);
  }

  public List<Bytes32> generateAccountKeyIds() {
    final List<Bytes32> keys = new ArrayList<>();
    keys.add(Parameters.VERSION_LEAF_KEY);
    keys.add(Parameters.BALANCE_LEAF_KEY);
    keys.add(Parameters.NONCE_LEAF_KEY);
    keys.add(Parameters.CODE_KECCAK_LEAF_KEY);
    return keys;
  }

  public List<Bytes32> generateCodeKeyIds(final Bytes code) {
    final List<Bytes32> keys = new ArrayList<>();
    keys.add(Parameters.CODE_KECCAK_LEAF_KEY);
    keys.add(Parameters.CODE_SIZE_LEAF_KEY);
    keys.addAll(trieKeyAdapter.chunkifyCode(code));
    return keys;
  }

  public List<Bytes32> generateStorageKeyIds(final Set<StorageSlotKey> storageSlotKeys) {
    return storageSlotKeys.stream()
        .map(storageSlotKey -> storageSlotKey.getSlotKey().orElseThrow())
        .map(Bytes32::wrap)
        .toList();
  }

  public Hasher createPreloadedHasher(
      final Address address,
      final List<Bytes32> accountKeyIds,
      final List<Bytes32> storageKeyIds,
      final List<Bytes32> codeChunkIds) {
    return new TrieKeyPreloaderHasher(
        trieKeyAdapter.manyAccountTrieKeys(address, accountKeyIds, storageKeyIds, codeChunkIds),
        hasher);
  }

  private static final class TrieKeyPreloaderHasher implements Hasher {
    private final Map<Bytes32, Bytes32> preloadedTrieKeys;
    private final Hasher hasher;

    private TrieKeyPreloaderHasher(
        final Map<Bytes32, Bytes32> preloadedTrieKeys, final Hasher hasher) {
      this.preloadedTrieKeys = preloadedTrieKeys;
      this.hasher = hasher;
    }

    @Override
    public Bytes commit(final Bytes32[] bytes32s) {
      return hasher.commit(bytes32s);
    }

    @Override
    public Bytes32 commitRoot(final Bytes32[] bytes32s) {
      return hasher.commitRoot(bytes32s);
    }

    @Override
    public Bytes32 trieKeyHash(final Bytes bytes, final Bytes32 bytes32) {
      return preloadedTrieKeys.getOrDefault(bytes32, hasher.trieKeyHash(bytes, bytes32));
    }

    @Override
    public Map<Bytes32, Bytes32> manyTrieKeyHashes(final Bytes bytes, final List<Bytes32> list) {
      return hasher.manyTrieKeyHashes(bytes, list);
    }

    @Override
    public Bytes32 groupToField(final Bytes bytes) {
      return hasher.groupToField(bytes);
    }
  }
}
