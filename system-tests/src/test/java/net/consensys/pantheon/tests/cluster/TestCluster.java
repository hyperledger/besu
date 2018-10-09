package net.consensys.pantheon.tests.cluster;

import static java.lang.Thread.sleep;
import static org.apache.commons.lang.StringUtils.join;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;

public final class TestCluster implements Closeable {
  private static final Logger LOG = LogManager.getLogger();

  private final DockerClient dockerClient;

  private final List<TestClusterNode> nodes = new ArrayList<>();
  private final Map<String, String> imageIds = new HashMap<>();
  private final List<String> bootNodes = new ArrayList<>();

  private final String clusterPrefix;

  /**
   * @param clusterPrefix Prefix to be used for all resources (eg data dirs, docker containers) used
   *     by this cluster. This will make it easier to cleanup after test runs in the case of a
   *     catastrophic failure where the test can not clean up after itself. Consider something like
   *     "myTestSuiteName-datetime"
   */
  public TestCluster(final String clusterPrefix) {
    Assertions.assertThat(clusterPrefix).isNotNull();
    this.clusterPrefix = clusterPrefix;
    dockerClient =
        DockerClientBuilder.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder().build())
            .build();
  }

  /** Returns an unmodifiable List of the cluster's nodes. */
  public List<TestClusterNode> getNodes() {
    return Collections.unmodifiableList(nodes);
  }

  /**
   * Creates a Pantheon boot-node in a docker container for this cluster
   *
   * @throws FileNotFoundException if the Dockerfile can't be found
   */
  public TestClusterNode addPantheonBootDockerNode(final String containerName)
      throws FileNotFoundException {
    throw new UnsupportedOperationException("Pantheon Boot Notes are not yet supported");
  }

  /**
   * Creates a Pantheon full-node in a docker container for this cluster
   *
   * @throws FileNotFoundException if the Dockerfile can't be found
   */
  public TestClusterNode addPantheonFullDockerNode(final String containerName)
      throws FileNotFoundException {
    if (bootNodes.isEmpty()) {
      throw new IllegalStateException(
          "Must result at least 1 boot node in this cluster before added regular nodes");
    }
    return addPantheonDockerNode(false, containerName, null);
  }

  /**
   * Create a Pantheon Docker container with an optional custom start command.
   *
   * @param isBootNode true if this node is a boot-only node.
   * @param containerName name for container
   * @param cmd Optional. Command to override start the container with. NULL will use the default
   *     from Dockerfile. One use is to be able to start both full-node and boot-node using the same
   *     image
   */
  private TestClusterNode addPantheonDockerNode(
      final boolean isBootNode, final String containerName, final String cmd)
      throws FileNotFoundException {
    final String imageName = clusterPrefix + ":pantheon";
    if (!imageIds.containsKey(imageName)) {
      // Only Create the image one time
      final String imageId = DockerUtils.createPantheonDockerImage(dockerClient, imageName);
      imageIds.put(imageName, imageId);
    }

    final List<String> env = new ArrayList<>();
    env.add("bootnodes=" + join(bootNodes, ','));

    final String hostName = containerName;
    // TODO: Run as non-root user
    final CreateContainerCmd createCtrCmd =
        dockerClient
            .createContainerCmd(imageName)
            .withName(containerName)
            .withPublishAllPorts(Boolean.TRUE)
            .withHostName(hostName)
            .withEnv(env)
            .withUser("root");
    if (cmd != null) {
      // Override start command if one was provided
      createCtrCmd.withCmd(cmd);
    } else {
      final List<String> args = new ArrayList<>();
      args.add("--bootnodes");
      args.add(join(bootNodes, ','));
      args.add("--rpc-listen");
      args.add("0.0.0.0:8545");
      args.add("--genesis");
      args.add("/opt/pantheon/genesis.json");
      createCtrCmd.withCmd(args);
    }
    final CreateContainerResponse createCtrResp = createCtrCmd.exec();
    final String containerId = createCtrResp.getId();
    LOG.info(
        "Starting Container: "
            + containerName
            + ", id="
            + containerId
            + ", cmd="
            + join(createCtrCmd.getCmd(), ' '));
    dockerClient.startContainerCmd(containerId).exec();

    final InspectContainerResponse startedContainer =
        dockerClient.inspectContainerCmd(containerId).exec();

    final String ipAddress = startedContainer.getNetworkSettings().getIpAddress();
    final Ports actualPorts = startedContainer.getNetworkSettings().getPorts();

    Ports.Binding hostPort = actualPorts.getBindings().get(new ExposedPort(8545))[0];
    final int jsonRpcPort = Integer.valueOf(hostPort.getHostPortSpec());

    hostPort = actualPorts.getBindings().get(new ExposedPort(30303, InternetProtocol.UDP))[0];
    final int discoveryPort = Integer.valueOf(hostPort.getHostPortSpec());

    final String filePath = "/var/lib/pantheon/node01/enode.log";
    // TODO: Rename getGethEnode, or make different method for pantheon
    final String eNode = getGethEnode(dockerClient, containerId, filePath);

    LOG.info("eNode = " + eNode);
    final InetSocketAddress discoveryAddress = new InetSocketAddress(ipAddress, 30303);
    final InetSocketAddress jsonRpcAddress = new InetSocketAddress(ipAddress, 8545);
    final InetSocketAddress hostDiscoveryAddress =
        new InetSocketAddress("localhost", discoveryPort);
    final InetSocketAddress hostJsonRpcAddress = new InetSocketAddress("localhost", jsonRpcPort);
    final TestClusterNode node =
        new TestDockerNode(
            dockerClient,
            hostName,
            containerId,
            isBootNode,
            discoveryAddress,
            jsonRpcAddress,
            hostDiscoveryAddress,
            hostJsonRpcAddress,
            eNode);
    nodes.add(node);
    LOG.info(String.format("Added node : %s", node));
    return node;
  }

  /**
   * Creates a Geth boot-node in a docker container for this cluster
   *
   * @throws FileNotFoundException if the Dockerfile can't be found
   */
  public TestClusterNode addGethBootDockerNode(final String containerName)
      throws FileNotFoundException {
    // TODO: Figure out sh -c syntax so this node is started the same way full nodes are, and
    // show up in the same way in ps list.
    //    TestClusterNode node = addGethDockerNode(containerName, "/bin/sh -c /runBootNode.sh");
    final TestClusterNode node = addGethDockerNode(true, containerName, "/runBootNode.sh");
    bootNodes.add(node.getEnode());
    return node;
  }

  /**
   * Creates a Geth full-node in a docker container for this cluster
   *
   * @throws FileNotFoundException if the Dockerfile can't be found
   */
  public TestClusterNode addGethFullDockerNode(final String containerName)
      throws FileNotFoundException {
    if (bootNodes.isEmpty()) {
      throw new IllegalStateException(
          "Must result at least 1 boot node in this cluster before added regular nodes");
    }
    return addGethDockerNode(false, containerName, null);
  }

  /**
   * Create a Geth Docker container with an optional custom start command.
   *
   * @param isBootNode true if this node is a boot-only node.
   * @param containerName name for container
   * @param cmd Optional. Command to override start the container with. NULL will use the default
   *     from Dockerfile. One use is to be able to start both Geth full-node and boot-node using the
   *     same image
   */
  private TestClusterNode addGethDockerNode(
      final boolean isBootNode, final String containerName, final String cmd)
      throws FileNotFoundException {
    final String imageName = clusterPrefix + ":geth";
    if (!imageIds.containsKey(imageName)) {
      // Only Create the image one time
      final String imageId = DockerUtils.createDockerImage(dockerClient, imageName, "geth");
      imageIds.put(imageName, imageId);
    }

    final List<String> env = new ArrayList<>();
    env.add("geth_bootnodes=" + join(bootNodes, ','));

    final String hostName = containerName;
    // TODO: Run as non-root user
    final CreateContainerCmd createCtrCmd =
        dockerClient
            .createContainerCmd(imageName)
            .withName(containerName)
            .withPublishAllPorts(Boolean.TRUE)
            .withHostName(hostName)
            .withEnv(env)
            .withUser("root");
    if (cmd != null) {
      // Override start command if one was provided
      createCtrCmd.withCmd(cmd);
    } else {
      final List<String> args = new ArrayList<>();
      args.add("--networkid");
      args.add("15"); // Test use networkid 15 so there is no chance of connecting to MainNet
      createCtrCmd.withCmd(args);
    }
    final CreateContainerResponse createCtrResp = createCtrCmd.exec();
    final String containerId = createCtrResp.getId();
    LOG.info("Starting Container: " + containerName + ", id=" + containerId);
    dockerClient.startContainerCmd(containerId).exec();

    final InspectContainerResponse startedContainer =
        dockerClient.inspectContainerCmd(containerId).exec();

    final String ipAddress = startedContainer.getNetworkSettings().getIpAddress();
    final Ports actualPorts = startedContainer.getNetworkSettings().getPorts();

    Ports.Binding hostPort = actualPorts.getBindings().get(new ExposedPort(8545))[0];
    final int jsonRpcPort = Integer.valueOf(hostPort.getHostPortSpec());

    hostPort = actualPorts.getBindings().get(new ExposedPort(30303, InternetProtocol.UDP))[0];
    final int discoveryPort = Integer.valueOf(hostPort.getHostPortSpec());

    final String filePath = "/var/lib/geth/node01/enode.log";
    final String eNode = getGethEnode(dockerClient, containerId, filePath);

    LOG.info("eNode = " + eNode);
    final InetSocketAddress discoveryAddress = new InetSocketAddress(ipAddress, 30303);
    final InetSocketAddress jsonRpcAddress = new InetSocketAddress(ipAddress, 8545);
    final InetSocketAddress hostDiscoveryAddress =
        new InetSocketAddress("localhost", discoveryPort);
    final InetSocketAddress hostJsonRpcAddress = new InetSocketAddress("localhost", jsonRpcPort);
    final TestClusterNode gethNode =
        new TestDockerNode(
            dockerClient,
            hostName,
            containerId,
            isBootNode,
            discoveryAddress,
            jsonRpcAddress,
            hostDiscoveryAddress,
            hostJsonRpcAddress,
            eNode);
    nodes.add(gethNode);
    LOG.info(String.format("Added node : %s", gethNode));
    return gethNode;
  }

  public static String getGethEnode(
      final DockerClient dockerClient, final String containerId, final String filePath) {
    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      // Todo, Poll instead of long sleep
      sleep(5000);
      final ExecCreateCmdResponse execCreateCmdResponse =
          dockerClient
              .execCreateCmd(containerId)
              .withAttachStdout(true)
              .withAttachStderr(true)
              .withCmd("/bin/cat", filePath)
              .exec();
      dockerClient
          .execStartCmd(execCreateCmdResponse.getId())
          .exec(new ExecStartResultCallback(stdout, stderr))
          .awaitCompletion();
    } catch (final InterruptedException e) {
      throw new RuntimeException("Unable to get Geth Enode.", e);
    }

    // Todo Validate that output is valid enode format
    // Todo validate stderr is empty
    return stdout.toString().trim();
  }

  public TestClusterNode addGethLocalNode(final String gethCmd, final String nodeNum)
      throws IOException {
    final SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd-HHmmss");

    final String imageName = "pantheontest-" + fmt.format(new Date()) + ":geth";
    final String hostName = "localhost";
    final String tmpPath = System.getProperty("java.io.tmpdir");
    if (tmpPath == null)
      throw new IllegalStateException("System Property 'java.io.tmpdir' must not be null");
    final File datadir = new File(tmpPath + "/" + imageName);
    datadir.mkdirs();
    final int ethPort = Integer.parseInt("304" + nodeNum);
    final int jsonRpcPort = Integer.parseInt("85" + nodeNum);
    final int discoveryPort = ethPort;

    final InetSocketAddress hostDiscoveryAddress = new InetSocketAddress(hostName, ethPort);
    final InetSocketAddress hostJsonRpcAddress = new InetSocketAddress(hostName, jsonRpcPort);

    final List<String> cmdList = new ArrayList<>();
    cmdList.add(gethCmd);
    cmdList.add("--datadir");
    cmdList.add(datadir.getAbsolutePath());
    cmdList.add("--port");
    cmdList.add(String.valueOf(ethPort));
    cmdList.add("--bootNodes");
    cmdList.add(join(bootNodes, ','));
    cmdList.add("--rpc");
    cmdList.add("--rpcapi");
    cmdList.add("eth,web3,admin");
    cmdList.add("--rpcaddr");
    cmdList.add("0.0.0.0");
    cmdList.add("--rpcport");
    cmdList.add(String.valueOf(jsonRpcPort));
    cmdList.add("rpc");

    final File log = new File(datadir, "geth.log");
    final ProcessBuilder pb =
        new ProcessBuilder(gethCmd)
            .directory(datadir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log));

    LOG.info("CmdList: " + join(cmdList, ' '));
    final Process p = pb.start();

    Assertions.assertThat(pb.redirectInput()).isEqualTo(ProcessBuilder.Redirect.PIPE);
    Assertions.assertThat(pb.redirectOutput().file()).isEqualTo(log);
    Assertions.assertThat(p.getInputStream().read()).isEqualTo(-1);

    final TestClusterNode gethNode =
        new TestGethLocalNode(
            datadir,
            imageName,
            false,
            hostDiscoveryAddress,
            hostJsonRpcAddress,
            hostDiscoveryAddress,
            hostJsonRpcAddress,
            getEnodeFromLog(log));
    nodes.add(gethNode);
    LOG.info(String.format("Added node : %s", gethNode));
    return gethNode;
  }

  public String getEnodeFromLog(final File file) throws IOException {
    return "enode://578e065716636c51c6d4b991c8299d920c8def1957e5fb2dc1c81d3ccf99a072bdcddad86e081e7f7d085a4bc4dbc2e04fe38c90cba810cee50af751e5e3ac70@192.168.71.128:30301";
    //    return grepFile(file, "UDP listener up *self=").iterator().next();
  }

  public Collection<String> grepFile(final File file, final String regex) throws IOException {
    final Set<String> results = new HashSet<>();
    final LineIterator it = FileUtils.lineIterator(file, "UTF-8");
    try {
      while (it.hasNext()) {
        final String line = it.nextLine();
        if (line.matches(regex)) {
          results.add(line);
        }
      }
    } finally {
      LineIterator.closeQuietly(it);
    }
    return results;
  }

  public TestClusterNode addParityNode() {
    if (bootNodes.isEmpty()) {
      throw new IllegalStateException(
          "Must result at least 1 boot node in this cluster before added regular nodes");
    }
    // TODO: Implement Parity Container Support
    throw new UnsupportedOperationException("Add ParityNode is not yet supported");
  }

  public TestClusterNode addCppEthNode() {
    if (bootNodes.isEmpty()) {
      throw new IllegalStateException(
          "Must result at least 1 boot node in this cluster before added regular nodes");
    }
    // TODO: Implement CppEth Container Support
    throw new UnsupportedOperationException("Add CppEthNode is not yet supported");
  }

  public TestClusterNode addPantheonNode() {
    if (bootNodes.isEmpty()) {
      throw new IllegalStateException(
          "Must result at least 1 boot node in this cluster before added regular nodes");
    }
    // TODO: Implement Pantheon Container Support
    throw new UnsupportedOperationException("Add PantheonNode is not yet supported");
  }

  @Override
  public void close() throws IOException {
    // TODO: If test times are too long, consider creating/destroying nodes in parallel
    Exception exToThrow = null;
    for (final Iterator<TestClusterNode> it = nodes.iterator(); it.hasNext(); ) {
      final TestClusterNode node = it.next();
      try {
        node.stop();
      } catch (final Exception e) {
        if (exToThrow == null) exToThrow = e;
        LOG.error("Error Stopping node.  continuing with close().  node = " + node, e);
      }

      try {
        node.delete();
        it.remove(); // Remove from our list of nodes only if it was successfully removed.
      } catch (final Exception e) {
        if (exToThrow == null) exToThrow = e;
        LOG.error("Error deleting node.  continuing with close().  node = " + node, e);
      }
    }

    for (final Iterator<String> it = imageIds.keySet().iterator(); it.hasNext(); ) {
      final String imageId = it.next();
      try {
        dockerClient.removeImageCmd(imageId).exec();
        it.remove();
      } catch (final Exception e) {
        if (exToThrow == null) exToThrow = e;
        LOG.error("Error removing docker image [" + imageId + "].  continuing with close()", e);
      }
    }

    try {
      dockerClient.close();
    } catch (final IOException e) {
      if (exToThrow == null) exToThrow = e;
      LOG.warn("Error closing dockerClient.  continuing with close()", e);
    }

    if (exToThrow != null) {
      throw new IOException("Error cleaning up cluster.", exToThrow);
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("TestCluster{");
    sb.append(", nodes=\n").append(join(nodes, "\n"));
    sb.append("\nimageIds=").append(imageIds);
    sb.append('}');
    return sb.toString();
  }
}
