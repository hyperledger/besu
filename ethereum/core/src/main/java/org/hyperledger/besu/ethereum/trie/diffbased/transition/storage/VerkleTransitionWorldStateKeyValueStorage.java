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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class VerkleTransitionWorldStateKeyValueStorage implements WorldStateKeyValueStorage,
    VerkleTransitionContext.VerkleTransitionSubscriber, AutoCloseable {
  final static Logger LOG = LoggerFactory.getLogger(VerkleTransitionWorldStateKeyValueStorage.class);

  final BonsaiWorldStateKeyValueStorage bonsaiKeyValueStorage;
  final VerkleWorldStateKeyValueStorage verkleKeyValueStorage;
  final AtomicReference<DiffBasedWorldStateKeyValueStorage> activeWorldStateStorage;
  final VerkleTransitionContext transitionContext;
  final Long subscriberId;

  public VerkleTransitionWorldStateKeyValueStorage(
      final StorageProvider provider,
      final MetricsSystem metricsSystem,
      final DataStorageConfiguration dataStorageConfiguration,
      final VerkleTransitionContext transitionContext) {
    this.bonsaiKeyValueStorage = new BonsaiWorldStateKeyValueStorage(provider, metricsSystem, dataStorageConfiguration);
    this.verkleKeyValueStorage = new VerkleWorldStateKeyValueStorage(provider, metricsSystem);
    this.transitionContext = transitionContext;
    // initialize with bonsai, rely on subscriber to update this:
    this.activeWorldStateStorage = new AtomicReference<>(bonsaiKeyValueStorage);
    this.subscriberId = transitionContext.subscribe(this);
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
    //TODO: create/return a transition-aware composed Updater
    return null;
  }

  @Override
  public void clear() {
    bonsaiKeyValueStorage.clear();
    verkleKeyValueStorage.clear();
  }

  /**
   * On transition started, switch active storage to verkle.
   */
  @Override
  public void onTransitionStarted() {
    activeWorldStateStorage.set(verkleKeyValueStorage);
  }

  /**
   *  On transition reverted, revert to bonsai active storage, and truncate verkle storage.
   */
  @Override
  public void onTransitionReverted() {
    activeWorldStateStorage.set(bonsaiKeyValueStorage);
    // truncate verkle trie if we are transitioning back (due to a reorg perhaps)
    verkleKeyValueStorage.clear();
    LOG.info("Truncated verkle trie on transition revert");
  }


  /**
   * Truncate bonsai trie on transition finalized.
   */
  @Override
  public void onTransitionFinalized() {
    bonsaiKeyValueStorage.clear();
    LOG.info("Truncated bonsai trie on transition complete");
  }

}
