package net.consensys.pantheon.tests;

import static net.consensys.pantheon.tests.cluster.NodeAdminRpcUtils.getPeersJson;

import net.consensys.pantheon.tests.cluster.TestCluster;
import net.consensys.pantheon.tests.cluster.TestClusterNode;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressWarnings("UnusedReturnValue")
public class LogClusterInfoTest extends ClusterTestBase {
  private static final Logger LOG = LogManager.getLogger();

  protected static TestCluster testCluster;

  @BeforeClass
  public static void createCluster() throws Exception {
    suiteName(LogClusterInfoTest.class);
    try {
      // TODO: If test times are too long, consider creating/destroying nodes in parallel
      testCluster = new TestCluster(suiteName().toLowerCase());
      addVerifiedGethBootCtr(testCluster, suiteName() + "-GethBoot00");
      addVerifiedGethCtr(testCluster, suiteName() + "-Geth01");
      addVerifiedGethCtr(testCluster, suiteName() + "-Geth02");
      addVerifiedPantheonCtr(testCluster, suiteName() + "-Pantheon01");
      addVerifiedPantheonCtr(testCluster, suiteName() + "-Pantheon02");
      // addVerifiedLocalGethProcess(cluster, "/usr/bin/geth", "04");
      Thread.sleep(2_000); // Wait for everything to settle
    } catch (final Exception e) {
      LOG.error("Unable to build private test cluster.", e);
      throw e;
    }
  }

  @AfterClass
  public static void destroyCluster() {
    try {
      testCluster.close();
    } catch (final Exception e) {
      LOG.error("Error destroying cluster.  Ignoring and continuing. cluster=" + testCluster, e);
    }
  }

  @Test
  public void logClusterInfo() throws Exception {
    LOG.info("Cluster: " + testCluster);
  }

  @Test
  public void logPeerPorts() throws Exception {
    for (final TestClusterNode node : testCluster.getNodes()) {
      if (node.isBootNode()) {
        continue; // Skip it.  Boot nodes don't have any interfaces to query
      }
      LOG.info("node=" + node);
      final JsonArray peersJson = getPeersJson(node);
      for (int i = 0; i < peersJson.size(); i++) {
        final JsonObject network = peersJson.getJsonObject(i).getJsonObject("network");
        final String local = network.getString("localAddress");
        final String remote = network.getString("remoteAddress");
        LOG.info(
            String.format(
                "Node %s discovered peer: local port %s, remote port %s",
                node.getNodeName(), local, remote));
      }
    }
  }

  @Test
  public void logAdminPeers() throws Exception {
    for (final TestClusterNode node : testCluster.getNodes()) {
      if (node.isBootNode()) {
        continue; // Skip it.  Boot nodes don't have any interfaces to query
      }
      LOG.info(
          String.format(
              "Node %s Admin.Peers:\n%s", node.getNodeName(), getPeersJson(node).encodePrettily()));
    }
  }
}
