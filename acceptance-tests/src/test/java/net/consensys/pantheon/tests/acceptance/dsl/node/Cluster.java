package net.consensys.pantheon.tests.acceptance.dsl.node;

import static net.consensys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Convert.toWei;

import net.consensys.pantheon.tests.acceptance.dsl.WaitUtils;
import net.consensys.pantheon.tests.acceptance.dsl.account.Account;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.utils.Convert.Unit;

public class Cluster implements AutoCloseable {

  private static final Logger LOG = LogManager.getLogger(Cluster.class);

  private final Map<String, PantheonNode> nodes = new HashMap<>();
  private final PantheonNodeRunner pantheonNodeRunner = PantheonNodeRunner.instance();

  public void start(final PantheonNode... nodes) {
    this.nodes.clear();

    final List<String> bootNodes = new ArrayList<>();

    for (final PantheonNode node : nodes) {
      this.nodes.put(node.getName(), node);
      bootNodes.add(node.enodeUrl());
    }

    for (final PantheonNode node : nodes) {
      node.bootnodes(bootNodes);
      node.start(pantheonNodeRunner);
    }

    for (final PantheonNode node : nodes) {
      awaitPeerDiscovery(node, nodes.length);
    }
  }

  public void stop() {
    for (final PantheonNode node : nodes.values()) {
      node.stop();
    }
    pantheonNodeRunner.shutdown();
  }

  @Override
  public void close() {
    for (final PantheonNode node : nodes.values()) {
      node.close();
    }
    pantheonNodeRunner.shutdown();
  }

  public PantheonNode create(final PantheonNodeConfig config) throws IOException {
    config.initSocket();
    final PantheonNode node =
        new PantheonNode(
            config.getName(),
            config.getSocketPort(),
            config.getMiningParameters(),
            config.getJsonRpcConfiguration(),
            config.getWebSocketConfiguration());
    config.closeSocket();
    return node;
  }

  private void awaitPeerDiscovery(final PantheonNode node, final int nodeCount) {
    if (node.jsonRpcEnabled()) {
      WaitUtils.waitFor(() -> assertThat(node.getPeerCount()).isEqualTo(nodeCount - 1));
    }
  }

  public void awaitPropagation(final Account account, final int expectedBalance) {
    awaitPropagation(account, String.valueOf(expectedBalance), Unit.ETHER);
  }

  public void awaitPropagation(
      final Account account, final String expectedBalance, final Unit balanceUnit) {

    for (final PantheonNode node : nodes.values()) {
      LOG.info(
          "Waiting for {} to have a balance of {} {} on node {}",
          account.getName(),
          expectedBalance,
          balanceUnit,
          node.getName());

      waitFor(
          () ->
              assertThat(node.getAccountBalance(account))
                  .isEqualTo(toWei(expectedBalance, balanceUnit).toBigIntegerExact()));
    }
  }
}
