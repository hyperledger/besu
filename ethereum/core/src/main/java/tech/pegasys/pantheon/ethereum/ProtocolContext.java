package tech.pegasys.pantheon.ethereum;

import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;

/**
 * Holds the mutable state used to track the current context of the protocol. This is primarily the
 * blockchain and world state archive, but can also hold arbitrary context required by a particular
 * consensus algorithm.
 *
 * @param <C> the type of the consensus algorithm context
 */
public class ProtocolContext<C> {
  private final MutableBlockchain blockchain;
  private final WorldStateArchive worldStateArchive;
  private final C consensusState;

  public ProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final C consensusState) {
    this.blockchain = blockchain;
    this.worldStateArchive = worldStateArchive;
    this.consensusState = consensusState;
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  public C getConsensusState() {
    return consensusState;
  }
}
