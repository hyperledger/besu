package net.consensys.pantheon.tests.acceptance.dsl;

import net.consensys.pantheon.tests.acceptance.dsl.account.Accounts;
import net.consensys.pantheon.tests.acceptance.dsl.node.Cluster;

import org.junit.After;

public class AcceptanceTestBase {

  protected Cluster cluster = new Cluster();
  protected Accounts accounts = new Accounts();
  protected JsonRpc jsonRpc = new JsonRpc(cluster);

  @After
  public void tearDownAcceptanceTestBase() throws Exception {
    cluster.close();
  }
}
