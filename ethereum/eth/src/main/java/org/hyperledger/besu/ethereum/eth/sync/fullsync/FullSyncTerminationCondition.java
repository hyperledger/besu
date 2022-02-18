package org.hyperledger.besu.ethereum.eth.sync.fullsync;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.function.Predicate;

import org.apache.tuweni.units.bigints.UInt256;

public interface FullSyncTerminationCondition extends Predicate<Blockchain> {
  static FullSyncTerminationCondition never() {
    return blockchain -> false;
  }

  static FullSyncTerminationCondition difficulty(final UInt256 difficulty) {
    return difficulty(Difficulty.of(difficulty));
  }

  static FullSyncTerminationCondition difficulty(final Difficulty difficulty) {
    return blockchain -> difficulty.greaterThan(blockchain.getChainHead().getTotalDifficulty());
  }
}
