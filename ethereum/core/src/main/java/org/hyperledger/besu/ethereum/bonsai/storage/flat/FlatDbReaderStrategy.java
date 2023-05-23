package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

public abstract class FlatDbReaderStrategy {

  protected final MetricsSystem metricsSystem;
  protected final KeyValueStorage accountStorage;
  protected final KeyValueStorage codeStorage;
  protected final KeyValueStorage storageStorage;

  protected final Counter getAccountCounter;
  protected final Counter getAccountFlatDatabaseCounter;
  protected final Counter getStorageValueCounter;
  protected final Counter getStorageValueFlatDatabaseCounter;

  public FlatDbReaderStrategy(
      final MetricsSystem metricsSystem,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage) {
    this.metricsSystem = metricsSystem;
    this.accountStorage = accountStorage;
    this.codeStorage = codeStorage;
    this.storageStorage = storageStorage;

    getAccountCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_total",
            "Total number of calls to getAccount");

    getAccountFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_account_flat_database",
            "Number of accounts found in the flat database");

    getStorageValueCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_total",
            "Total number of calls to getStorageValueBySlotHash");

    getStorageValueFlatDatabaseCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "get_storagevalue_flat_database",
            "Number of storage slots found in the flat database");
  }

  public Optional<Bytes> getAccount(final Hash accountHash) {
    return getAccount(Optional::empty, (location, hash) -> Optional.empty(), accountHash);
  }

  public Optional<Bytes> getAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash) {
    return accountStorage.get(accountHash.toArrayUnsafe()).map(Bytes::wrap);
  }

  public Optional<Bytes> getCode(final Bytes32 codeHash, final Hash accountHash) {
    if (codeHash.equals(Hash.EMPTY)) {
      return Optional.of(Bytes.EMPTY);
    } else {
      return codeStorage
          .get(accountHash.toArrayUnsafe())
          .map(Bytes::wrap)
          .filter(b -> Hash.hash(b).equals(codeHash));
    }
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Hash accountHash, final StorageSlotKey storageSlotKey) {
    return getStorageValueByStorageSlotKey(
        Optional::empty,
        Optional::empty,
        (location, hash) -> Optional.empty(),
        accountHash,
        storageSlotKey);
  }

  public Optional<Bytes> getStorageValueByStorageSlotKey(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final Supplier<Optional<Hash>> storageRootSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final StorageSlotKey storageSlotKey) {
    return storageStorage
        .get(Bytes.concatenate(accountHash, storageSlotKey.getSlotHash()).toArrayUnsafe())
        .map(Bytes::wrap);
  }

  public void clearAll() {
    accountStorage.clear();
    storageStorage.clear();
    codeStorage.clear();
  }

  public void clearAccountAndStorageDatabase() {
    accountStorage.clear();
    storageStorage.clear();
  }

  public Map<Bytes32, Bytes> streamAccountFlatDatabase(
      final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    final Stream<Pair<Bytes32, Bytes>> pairStream =
        accountStorage
            .streamFromKey(startKeyHash.toArrayUnsafe())
            .limit(max)
            .map(pair -> new Pair<>(Bytes32.wrap(pair.getKey()), Bytes.wrap(pair.getValue())))
            .takeWhile(pair -> pair.getFirst().compareTo(endKeyHash) <= 0);

    final TreeMap<Bytes32, Bytes> collected =
        pairStream.collect(
            Collectors.toMap(Pair::getFirst, Pair::getSecond, (v1, v2) -> v1, TreeMap::new));
    pairStream.close();
    return collected;
  }

  public Map<Bytes32, Bytes> streamStorageFlatDatabase(
      final Hash accountHash, final Bytes startKeyHash, final Bytes32 endKeyHash, final long max) {
    final Stream<Pair<Bytes32, Bytes>> pairStream =
        storageStorage
            .streamFromKey(Bytes.concatenate(accountHash, startKeyHash).toArrayUnsafe())
            .takeWhile(pair -> Bytes.wrap(pair.getKey()).slice(0, Hash.SIZE).equals(accountHash))
            .limit(max)
            .map(
                pair ->
                    new Pair<>(
                        Bytes32.wrap(Bytes.wrap(pair.getKey()).slice(Hash.SIZE)),
                        RLP.encodeValue(Bytes.wrap(pair.getValue()).trimLeadingZeros())))
            .takeWhile(pair -> pair.getFirst().compareTo(endKeyHash) <= 0);

    final TreeMap<Bytes32, Bytes> collected =
        pairStream.collect(
            Collectors.toMap(Pair::getFirst, Pair::getSecond, (v1, v2) -> v1, TreeMap::new));
    pairStream.close();
    return collected;
  }
}
