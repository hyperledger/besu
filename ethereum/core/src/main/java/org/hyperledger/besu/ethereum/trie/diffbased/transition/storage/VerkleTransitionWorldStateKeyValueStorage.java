package org.hyperledger.besu.ethereum.trie.diffbased.transition.storage;

import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.transition.VerkleTransitionContext;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.storage.VerkleWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerkleTransitionWorldStateKeyValueStorage
    implements WorldStateKeyValueStorage,
        AutoCloseable {
  private static final Logger LOG =
      LoggerFactory.getLogger(VerkleTransitionWorldStateKeyValueStorage.class);

  final BonsaiWorldStateKeyValueStorage bonsaiKeyValueStorage;
  final VerkleWorldStateKeyValueStorage verkleKeyValueStorage;
  final long blockTimestamp;

  public VerkleTransitionWorldStateKeyValueStorage(
      final StorageProvider provider,
      final long blockTimestamp,
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration) {
    this.bonsaiKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(provider, metricsSystem, dataStorageConfiguration);
    this.verkleKeyValueStorage = new VerkleWorldStateKeyValueStorage(provider, metricsSystem);
    this.blockTimestamp = blockTimestamp;
  }

  @Override
  public void close() throws Exception {
    // TODO: it might be safer to close the storage directly
    //  rather than deferring to both, since they share segments
    bonsaiKeyValueStorage.close();
    verkleKeyValueStorage.close();
  }

  @Override
  public DataStorageFormat getDataStorageFormat() {
    return DataStorageFormat.VERKLE_TRANSITION;
  }

  @Override
  public Updater updater() {
    // if we are in transition, we should only use the verkle updater, merkle trie should be frozen
    return verkleKeyValueStorage.updater();
  }

  @Override
  public void clear() {
    bonsaiKeyValueStorage.clear();
    verkleKeyValueStorage.clear();
  }
}
