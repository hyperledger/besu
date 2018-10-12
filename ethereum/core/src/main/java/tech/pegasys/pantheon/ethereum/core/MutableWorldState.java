package tech.pegasys.pantheon.ethereum.core;

public interface MutableWorldState extends WorldState, MutableWorldView {

  /**
   * Creates an independent copy of this world state initially equivalent to this world state.
   *
   * @return a copy of this world state.
   */
  MutableWorldState copy();

  /** Persist accumulated changes to underlying storage. */
  void persist();
}
