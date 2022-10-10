package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.storage.StorageProvider;

public class BonsaiWorldStateKeyValueStorageFactory {

  private final boolean useBonsaiLight;

  public BonsaiWorldStateKeyValueStorageFactory(final boolean useBonsaiLight) {
    this.useBonsaiLight = useBonsaiLight;
  }

  public BonsaiWorldStateKeyValueStorage create(final StorageProvider storageProvider) {
    if (!useBonsaiLight) {
      return new BonsaiWorldStateKeyValueStorage(storageProvider);
    } else {
      return new BonsaiLightWorldStateKeyValueStorage(storageProvider);
    }
  }
}
