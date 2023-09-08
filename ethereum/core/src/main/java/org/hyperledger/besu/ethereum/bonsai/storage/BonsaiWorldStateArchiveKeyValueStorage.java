package org.hyperledger.besu.ethereum.bonsai.storage;

import org.hyperledger.besu.ethereum.bonsai.BonsaiContext;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.util.function.Supplier;

public class BonsaiWorldStateArchiveKeyValueStorage extends BonsaiWorldStateKeyValueStorage {
  Supplier<BonsaiContext> contextSupplier;

  public BonsaiWorldStateArchiveKeyValueStorage(
      StorageProvider provider,
      ObservableMetricsSystem metricsSystem,
      Supplier<BonsaiContext> contextSupplier) {
    super(provider, metricsSystem);
    this.contextSupplier = contextSupplier;
  }
}
