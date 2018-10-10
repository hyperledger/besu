package net.consensys.pantheon.tests.acceptance;

import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonMinerNode;
import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonNode;

import net.consensys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.Before;
import org.junit.Test;

public class PantheonClusterAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode minerNode;
  private PantheonNode fullNode;

  @Before
  public void setUp() throws Exception {
    minerNode = cluster.create(pantheonMinerNode("node1"));
    fullNode = cluster.create(pantheonNode("node2"));
    cluster.start(minerNode, fullNode);
  }

  @Test
  public void shouldConnectToOtherPeer() {
    jsonRpc.waitForPeersConnected(minerNode, 1);
    jsonRpc.waitForPeersConnected(fullNode, 1);
  }
}
