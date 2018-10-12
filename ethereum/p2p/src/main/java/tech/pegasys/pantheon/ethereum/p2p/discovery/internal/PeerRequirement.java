package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import java.util.Collection;

public interface PeerRequirement {

  boolean hasSufficientPeers();

  static PeerRequirement aggregateOf(final Collection<? extends PeerRequirement> peers) {
    return () -> peers.stream().allMatch(PeerRequirement::hasSufficientPeers);
  }
}
