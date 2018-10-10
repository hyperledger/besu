package net.consensys.pantheon.tests;

import static java.util.stream.Collectors.joining;

import net.consensys.pantheon.tests.cluster.NodeAdminRpcUtils;
import net.consensys.pantheon.tests.cluster.TestCluster;
import net.consensys.pantheon.tests.cluster.TestClusterNode;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public abstract class ClusterTestBase {
  private static final Logger LOG = LogManager.getLogger();

  @Rule
  public TestRule watcher =
      new TestWatcher() {
        @Override
        protected void starting(final Description description) {
          LOG.info("Starting test: " + description.getMethodName());
        }

        @Override
        protected void finished(final Description description) {
          LOG.info("Finished test: " + description.getMethodName());
        }
      };

  // TODO: I need to remember how I used to make this work in one shot, even with static
  // @BeforeClass
  protected static String suiteStartTime = null;
  protected static String suiteName = null;

  @BeforeClass
  public static void setTestSuiteStartTime() {
    final SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd-HHmmss");
    suiteStartTime = fmt.format(new Date());
  }

  public static void suiteName(final Class<?> clazz) {
    suiteName = clazz.getSimpleName() + "-" + suiteStartTime;
  }

  public static String suiteName() {
    return suiteName;
  }

  @BeforeClass
  public static void printSystemProperties() {
    final String env =
        System.getenv()
            .entrySet()
            .stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(joining(System.getProperty("line.separator")));

    final String s =
        System.getProperties()
            .entrySet()
            .stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(joining(System.getProperty("line.separator")));

    LOG.info("System Properties\n" + s);
    LOG.info("Environment\n" + env);
  }

  /**
   * Adds and verifies a Pantheon full-node container to the cluster
   *
   * @param cluster cluster to add node to
   * @param containerName name to give the new container
   */
  protected static TestClusterNode addVerifiedPantheonCtr(
      final TestCluster cluster, final String containerName) throws Exception {
    final TestClusterNode node = cluster.addPantheonFullDockerNode(containerName);
    Assertions.assertThat(cluster.getNodes()).contains(node);
    NodeAdminRpcUtils.testWeb3ClientVersionPantheon(node);
    // TODO: Better verifications before handing off for testing
    return node;
  }

  /**
   * Adds and verifies a Geth full-node container to the cluster
   *
   * @param cluster cluster to add node to
   * @param containerName name to give the new container
   */
  protected static TestClusterNode addVerifiedGethCtr(
      final TestCluster cluster, final String containerName) throws Exception {
    final TestClusterNode node = cluster.addGethFullDockerNode(containerName);
    Assertions.assertThat(cluster.getNodes()).contains(node);
    NodeAdminRpcUtils.testWeb3ClientVersionGeth(node);
    // TODO: Better verifications before handing off for testing
    return node;
  }

  /**
   * Adds and verifies a Geth boot-node container to the cluster. Generally, only one is required in
   * the cluster, but adding more than one should not be harmful.
   *
   * @param cluster cluster to add node to
   * @param containerName name to give the new container
   */
  protected static TestClusterNode addVerifiedGethBootCtr(
      final TestCluster cluster, final String containerName) throws Exception {
    final TestClusterNode node = cluster.addGethBootDockerNode(containerName);
    Assertions.assertThat(cluster.getNodes()).contains(node);
    // TODO: Better verifications before handing off for testing
    return node;
  }

  /**
   * Adds and verifies a Geth local process to the cluster. A new tmp data directory will be created
   * for each local process node.
   *
   * <p>TODO: Make this play nice with other nodes running in docker containers.
   *
   * @param cluster cluster to add node to
   * @param gethCmd path to the geth command. ie /usr/bin/geth
   * @param nodeNum two-digit node number. This will be used in node naming and port numbering.
   */
  @SuppressWarnings("unused")
  protected static TestClusterNode addVerifiedLocalGethProcess(
      final TestCluster cluster, final String gethCmd, final String nodeNum) throws Exception {
    final TestClusterNode node = cluster.addGethLocalNode(gethCmd, nodeNum);
    Assertions.assertThat(cluster.getNodes()).contains(node);
    NodeAdminRpcUtils.testWeb3ClientVersionGeth(node);
    // TODO: Better verifications before handing off for testing
    return node;
  }

  /** Helper method to stand up a private cluster. */
  @SuppressWarnings("unused")
  private void buildVerifiedCluster(
      final TestCluster cluster,
      final int numPantheonNodes,
      final int numGethNodes,
      final int numParityNodes) {
    // TODO: Implement if useful
    // TODO: If test times are too long, consider creating/destroying nodes in parallel
  }
}
