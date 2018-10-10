package net.consensys.pantheon.ethereum.core;

public interface MutableWorldView extends WorldView {

  /**
   * Creates a updater for this mutable world view.
   *
   * @return a new updater for this mutable world view. On commit, change made to this updater will
   *     become visible on this view.
   */
  WorldUpdater updater();
}
