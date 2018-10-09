package net.consensys.pantheon.tests.cluster;

import java.io.File;
import java.net.InetSocketAddress;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestGethLocalNode extends TestClusterNode {
  private static final Logger LOG = LogManager.getLogger();

  protected File dataDir;

  public TestGethLocalNode(
      final File dataDir,
      final String nodeName,
      final boolean isBootNode,
      final InetSocketAddress discoveryAddress,
      final InetSocketAddress jsonRpcAddress,
      final InetSocketAddress hostDiscoveryAddress,
      final InetSocketAddress hostJsonRpcAddress,
      final String enode) {
    super(
        nodeName,
        isBootNode,
        discoveryAddress,
        jsonRpcAddress,
        hostDiscoveryAddress,
        hostJsonRpcAddress,
        enode);
    this.dataDir = dataDir;
  }

  @Override
  public void start() {
    // TODO: Implement Start()
    LOG.warn("TODO: Implement Start()");
    //    LOG.info("Calling stop on node {}", this);
  }

  @Override
  public void stop() {
    // TODO: Implement Stop()
    LOG.warn("TODO: Implement Stop()");
    //    LOG.info("Calling stop on node {}", this);

  }

  @Override
  public void delete() {
    // TODO: Implement Delete()
    LOG.warn("TODO: Implement DELETE()");
    //    LOG.info("Calling delete on node {}", this);

  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("nodeName", nodeName)
        .add("isBootNode", isBootNode)
        .add("discoveryAddress", discoveryAddress)
        .add("jsonRpcAddress", jsonRpcAddress)
        .add("hostDiscoveryAddress", hostDiscoveryAddress)
        .add("hostJsonRpcAddress", hostJsonRpcAddress)
        .add("enode", enode)
        .add("dataDir", dataDir)
        .toString();
  }
}
