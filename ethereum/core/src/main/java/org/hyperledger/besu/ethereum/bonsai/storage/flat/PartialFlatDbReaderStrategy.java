package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.StoredNodeFactory;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

public class PartialFlatDbReaderStrategy extends FlatDbReaderStrategy {

  protected final Counter getAccountMerkleTrieCounter;
  protected final Counter getAccountMissingMerkleTrieCounter;

  protected final Counter getStorageValueMerkleTrieCounter;
  protected final Counter getStorageValueMissingMerkleTrieCounter;

  public PartialFlatDbReaderStrategy(
      final MetricsSystem metricsSystem,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage) {
    super(metricsSystem, accountStorage, codeStorage, storageStorage);
    getAccountMerkleTrieCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_merkle_trie",
            "Number of accounts not found in the flat database, but found in the merkle trie");

    getAccountMissingMerkleTrieCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_missing_merkle_trie",
            "Number of accounts not found (either in the flat database or the merkle trie)");

    getStorageValueMerkleTrieCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_merkle_trie",
            "Number of storage slots not found in the flat database, but found in the merkle trie");

    getStorageValueMissingMerkleTrieCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_missing_merkle_trie",
            "Number of storage slots not found (either in the flat database or in the merkle trie)");
  }

  @Override
  public Optional<Bytes> getAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash) {
    getAccountCounter.inc();
    Optional<Bytes> response = super.getAccount(accountHash);
    if (response.isEmpty()) {
      // after a snapsync/fastsync we only have the trie branches.
      final Optional<Bytes> worldStateRootHash = worldStateRootHashSupplier.get();
      if (worldStateRootHash.isPresent()) {
        response =
            new StoredMerklePatriciaTrie<>(
                    new StoredNodeFactory<>(nodeLoader, Function.identity(), Function.identity()),
                    Bytes32.wrap(worldStateRootHash.get()))
                .get(accountHash);
        if (response.isEmpty()) getAccountMissingMerkleTrieCounter.inc();
        else getAccountMerkleTrieCounter.inc();
      }
    } else {
      getAccountFlatDatabaseCounter.inc();
    }

    return response;
  }

  @Override
  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final Supplier<Optional<Hash>> storageRootSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    getStorageValueCounter.inc();
    Optional<Bytes> response = super.getStorageValueByStorageSlotKey(accountHash, storageSlotKey);
    if (response.isEmpty()) {
      final Optional<Hash> storageRoot = storageRootSupplier.get();
      final Optional<Bytes> worldStateRootHash = worldStateRootHashSupplier.get();
      if (storageRoot.isPresent() && worldStateRootHash.isPresent()) {
        response =
            new StoredMerklePatriciaTrie<>(
                    new StoredNodeFactory<>(nodeLoader, Function.identity(), Function.identity()),
                    storageRoot.get())
                .get(storageSlotKey.getSlotHash())
                .map(bytes -> Bytes32.leftPad(RLP.decodeValue(bytes)));
        if (response.isEmpty()) getStorageValueMissingMerkleTrieCounter.inc();
        else getStorageValueMerkleTrieCounter.inc();
      }
    } else {
      getStorageValueFlatDatabaseCounter.inc();
    }
    return response;
  }
}
