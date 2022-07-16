package org.hyperledger.besu.consensus.merge;

import static org.hyperledger.besu.ethereum.eth.manager.EthPeers.CHAIN_HEIGHT;

import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class TransitionBestPeerComparator implements Comparator<EthPeer>, MergeStateHandler {

  private static final AtomicReference<Difficulty> terminalTotalDifficulty =
      new AtomicReference<>();

  static final BiFunction<EthPeer, Difficulty, BigInteger> distanceFromTTD =
      (a, ttd) ->
          a.chainState()
              .getEstimatedTotalDifficulty()
              .getAsBigInteger()
              .subtract(ttd.getAsBigInteger())
              .abs()
              .negate();

  public static final Comparator<EthPeer> EXACT_DIFFICULTY =
      (a, b) -> {
        var ttd = terminalTotalDifficulty.get();
        var aDelta = distanceFromTTD.apply(a, ttd);
        var bDelta = distanceFromTTD.apply(b, ttd);
        return aDelta.compareTo(bDelta);
      };

  public static final Comparator<EthPeer> BEST_MERGE_CHAIN =
      EXACT_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  public TransitionBestPeerComparator(final Difficulty configuredTerminalTotalDifficulty) {
    terminalTotalDifficulty.set(configuredTerminalTotalDifficulty);
  }

  @Override
  public void mergeStateChanged(
      final boolean isPoS, final Optional<Difficulty> difficultyStoppedAt) {
    if (isPoS && difficultyStoppedAt.isPresent()) {
      terminalTotalDifficulty.set(difficultyStoppedAt.get());
    }
  }

  @Override
  public int compare(final EthPeer o1, final EthPeer o2) {
    return BEST_MERGE_CHAIN.compare(o1, o2);
  }
}
