package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.BlockHeader;

import java.math.BigInteger;

/** Calculates block difficulties. */
@FunctionalInterface
public interface DifficultyCalculator<C> {

  /**
   * Calculates the block difficulty for a block.
   *
   * @param time the time the block was generated
   * @param parent the block's parent block header
   * @param context the context in which the difficulty calculator should operate
   * @return the block difficulty
   */
  BigInteger nextDifficulty(long time, BlockHeader parent, ProtocolContext<C> context);
}
