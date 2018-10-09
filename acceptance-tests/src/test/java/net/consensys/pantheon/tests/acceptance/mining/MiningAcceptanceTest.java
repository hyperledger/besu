package net.consensys.pantheon.tests.acceptance.mining;

import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonMinerNode;
import static org.web3j.utils.Convert.Unit.ETHER;

import net.consensys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import net.consensys.pantheon.tests.acceptance.dsl.account.Account;
import net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.Before;
import org.junit.Test;

public class MiningAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = cluster.create(pantheonMinerNode("miner1"));
    cluster.start(minerNode);
  }

  @Test
  public void shouldMineTransactions() {
    final Account fromAccount = accounts.createAccount("account1", "50", ETHER, minerNode);
    final Account toAccount = accounts.createAccount("account2", "0", ETHER, minerNode);
    accounts.waitForAccountBalance(fromAccount, 50, minerNode);

    accounts.incrementalTransfer(fromAccount, toAccount, 1, minerNode);
    accounts.waitForAccountBalance(toAccount, 1, minerNode);

    accounts.incrementalTransfer(fromAccount, toAccount, 2, minerNode);
    accounts.waitForAccountBalance(toAccount, 3, minerNode);

    accounts.incrementalTransfer(fromAccount, toAccount, 3, minerNode);
    accounts.waitForAccountBalance(toAccount, 6, minerNode);

    accounts.incrementalTransfer(fromAccount, toAccount, 4, minerNode);
    accounts.waitForAccountBalance(toAccount, 10, minerNode);

    accounts.incrementalTransfer(fromAccount, toAccount, 5, minerNode);
    accounts.waitForAccountBalance(toAccount, 15, minerNode);
  }
}
