package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.NodeLoader;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

public class ArchiveFlatDbStrategy extends FullFlatDbStrategy {
  private final Supplier<BonsaiContext> contextSupplier;

  public ArchiveFlatDbStrategy(
      Supplier<BonsaiContext> contextSupplier, MetricsSystem metricsSystem) {
    super(metricsSystem);
    this.contextSupplier = contextSupplier;
  }

  @Override
  public Optional<Bytes> getFlatAccount(
      final Supplier<Optional<Bytes>> worldStateRootHashSupplier,
      final NodeLoader nodeLoader,
      final Hash accountHash,
      final SegmentedKeyValueStorage storage) {
    getAccountCounter.inc();

    // use getNearest() with an account key that is suffixed by the block context
    final Optional<Bytes> accountFound =
        storage.getNearest(ACCOUNT_INFO_STATE, accountHash.toArrayUnsafe()).map(Bytes::wrap);

    if (accountFound.isPresent()) {
      getAccountFoundInFlatDatabaseCounter.inc();
    } else {
      getAccountNotFoundInFlatDatabaseCounter.inc();
    }
    return accountFound;
  }
}
