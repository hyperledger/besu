package net.consensys.pantheon.ethereum.core;

import java.util.stream.Stream;

/**
 * A specific state of the world.
 *
 * <p>Note that while this interface represents an immutable view of a world state (it doesn't have
 * mutation methods), it does not guarantee in and of itself that the underlying implementation is
 * not mutable. In other words, objects implementing this interface are not guaranteed to be
 * thread-safe, though some particular implementations may provide such guarantees.
 */
public interface WorldState extends WorldView {

  /**
   * The root hash of the world state this represents.
   *
   * @return the world state root hash.
   */
  Hash rootHash();

  /**
   * A stream of all the accounts in this world state.
   *
   * @return a stream of all the accounts (in no particular order) contained in the world state
   *     represented by the root hash of this object at the time of the call.
   */
  Stream<Account> accounts();
}
