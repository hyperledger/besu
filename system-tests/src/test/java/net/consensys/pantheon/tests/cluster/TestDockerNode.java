package net.consensys.pantheon.tests.cluster;

import java.net.InetSocketAddress;

import com.github.dockerjava.api.DockerClient;
import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestDockerNode extends TestClusterNode {
  private static final Logger LOG = LogManager.getLogger();

  protected String containerId;
  protected DockerClient dockerClient;

  public TestDockerNode(
      final DockerClient dockerClient,
      final String nodeName,
      final String containerId,
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
    this.containerId = containerId;
    this.dockerClient = dockerClient;
  }

  public String getContainerId() {
    return containerId;
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
        .add("containerId", containerId)
        .add("dockerClient", dockerClient)
        .toString();
  }

  @Override
  public void start() {
    LOG.info("Calling stop on node {}", this);
    dockerClient.startContainerCmd(containerId).exec();
  }

  @Override
  public void stop() {
    LOG.info("Calling stop on node {}", this);
    dockerClient.stopContainerCmd(containerId).exec();
  }

  @Override
  public void delete() {
    LOG.info("Calling delete on node {}", this);
    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
  }
}
