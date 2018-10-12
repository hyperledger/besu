package net.consensys.pantheon.ethereum.core;

/**
 * An interface for creating the block hash given a {@link BlockHeader}.
 *
 * <p>The algorithm to create the block hash may vary depending on the consensus mechanism used by
 * the chain.
 */
@FunctionalInterface
public interface BlockHashFunction {

  /**
   * Create the hash for a given BlockHeader.
   *
   * @param header the header to create the block hash from
   * @return a {@link Hash} containing the block hash.
   */
  Hash apply(BlockHeader header);
}
