/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.acceptance.mining;

import static org.web3j.utils.Convert.Unit.ETHER;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonMinerNode;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.account.Account;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
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
