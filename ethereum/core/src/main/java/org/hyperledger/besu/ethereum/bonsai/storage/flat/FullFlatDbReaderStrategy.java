package org.hyperledger.besu.ethereum.bonsai.storage.flat;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

public class FullFlatDbReaderStrategy extends FlatDbReaderStrategy {

  public FullFlatDbReaderStrategy(
      final MetricsSystem metricsSystem,
      final KeyValueStorage accountStorage,
      final KeyValueStorage codeStorage,
      final KeyValueStorage storageStorage) {
    super(metricsSystem, accountStorage, codeStorage, storageStorage);
  }
}
