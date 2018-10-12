package tech.pegasys.pantheon.ethereum.p2p.discovery.internal;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.p2p.discovery.DiscoveryPeer;
import tech.pegasys.pantheon.ethereum.p2p.discovery.PeerDiscoveryTestHelper;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerTable.AddResult.Outcome;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;

import org.junit.Test;

public class PeerTableTest {

  @Test
  public void addPeer() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer[] peers =
        PeerDiscoveryTestHelper.generateDiscoveryPeers(PeerDiscoveryTestHelper.generateKeyPairs(5));

    for (final DiscoveryPeer peer : peers) {
      final PeerTable.AddResult result = table.tryAdd(peer);
      assertThat(result.getOutcome()).isEqualTo(Outcome.ADDED);
    }

    assertThat(table.getAllPeers()).hasSize(5);
  }

  @Test
  public void addSelf() {
    final DiscoveryPeer self = new DiscoveryPeer(Peer.randomId(), "127.0.0.1", 12345, 12345);
    final PeerTable table = new PeerTable(self.getId(), 16);
    final PeerTable.AddResult result = table.tryAdd(self);

    assertThat(result.getOutcome()).isEqualTo(Outcome.SELF);
    assertThat(table.getAllPeers()).hasSize(0);
  }

  @Test
  public void peerExists() {
    final PeerTable table = new PeerTable(Peer.randomId(), 16);
    final DiscoveryPeer peer =
        PeerDiscoveryTestHelper.generateDiscoveryPeers(PeerDiscoveryTestHelper.generateKeyPairs(1))[
            0];

    assertThat(table.tryAdd(peer).getOutcome()).isEqualTo(Outcome.ADDED);

    assertThat(table.tryAdd(peer))
        .satisfies(
            result -> {
              assertThat(result.getOutcome()).isEqualTo(Outcome.ALREADY_EXISTED);
              assertThat(result.getEvictionCandidate()).isNull();
            });
  }
}
