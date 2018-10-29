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
package tech.pegasys.pantheon.tests.acceptance.dsl;

import tech.pegasys.pantheon.tests.acceptance.dsl.account.Accounts;
import tech.pegasys.pantheon.tests.acceptance.dsl.blockchain.Blockchain;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.EthTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Web3Transactions;

import org.junit.After;

public class AcceptanceTestBase {

  protected final Accounts accounts;
  protected final Blockchain blockchain;
  protected final Cluster cluster;
  protected final EthTransactions eth;
  protected final JsonRpc jsonRpc;
  protected final Transactions transactions;
  protected final Web3Transactions web3;

  protected AcceptanceTestBase() {
    eth = new EthTransactions();
    accounts = new Accounts(eth);
    blockchain = new Blockchain(eth);
    cluster = new Cluster();
    jsonRpc = new JsonRpc(cluster);
    transactions = new Transactions(accounts);
    web3 = new Web3Transactions();
  }

  @After
  public void tearDownAcceptanceTestBase() {
    cluster.close();
  }
}
