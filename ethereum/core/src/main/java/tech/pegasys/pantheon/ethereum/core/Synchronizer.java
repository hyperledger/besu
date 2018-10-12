package tech.pegasys.pantheon.ethereum.core;

import java.util.Optional;

/** Provides an interface to block synchronization processes. */
public interface Synchronizer {

  public void start();

  /**
   * @return the status, based on SyncingResult When actively synchronizing blocks, alternatively
   *     empty
   */
  Optional<SyncStatus> getSyncStatus();
}
