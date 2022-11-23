package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unchecked")
public class BonsaiIntermediateCommitCountUpdater<WORLDSTATE extends WorldStateStorage> {

  private final WORLDSTATE worldStateStorage;

  private final Integer maxElement;
  final AtomicInteger eltRemoved = new AtomicInteger();
  private WORLDSTATE.Updater updater;

  public BonsaiIntermediateCommitCountUpdater(
      final WORLDSTATE worldStateStorage, final Integer maxTransactions) {
    this.updater = worldStateStorage.updater();
    this.worldStateStorage = worldStateStorage;
    this.maxElement = maxTransactions;
  }

  public WorldStateStorage.Updater getUpdater() {
    if (eltRemoved.incrementAndGet() % maxElement == 0) {
      updater.commit();
      updater = worldStateStorage.updater();
    }
    return updater;
  }

  public void close() {
    updater.commit();
  }
}
