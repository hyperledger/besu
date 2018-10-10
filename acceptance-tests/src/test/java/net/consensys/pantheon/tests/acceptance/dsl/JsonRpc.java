package net.consensys.pantheon.tests.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.tests.acceptance.dsl.node.Cluster;
import net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNode;

public class JsonRpc {

  private final Cluster nodes;

  public JsonRpc(final Cluster nodes) {
    this.nodes = nodes;
  }

  public void waitForPeersConnected(final PantheonNode node, final int expectedNumberOfPeers) {
    WaitUtils.waitFor(() -> assertThat(node.getPeerCount()).isEqualTo(expectedNumberOfPeers));
  }
}
