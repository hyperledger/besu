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
import tech.pegasys.pantheon.tests.acceptance.dsl.contract.ContractVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Admin;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Clique;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eth;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Ibft2;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Login;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Net;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Perm;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Web3;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonNodeFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.permissioning.PermissionedNodeBuilder;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.account.AccountTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.clique.CliqueTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.contract.ContractTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eth.EthTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.ibft2.Ibft2Transactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.net.NetTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.web3.Web3Transactions;

import org.junit.After;

public class AcceptanceTestBase {

  protected final Accounts accounts;
  protected final AccountTransactions accountTransactions;
  protected final Admin admin;
  protected final Blockchain blockchain;
  protected final Clique clique;
  protected final CliqueTransactions cliqueTransactions;
  protected final Cluster cluster;
  protected final ContractVerifier contractVerifier;
  protected final Eth eth;
  protected final EthTransactions ethTransactions;
  protected final Ibft2Transactions ibftTwoTransactions;
  protected final Ibft2 ibftTwo;
  protected final Login login;
  protected final Net net;
  protected final PantheonNodeFactory pantheon;
  protected final Perm perm;
  protected final PermissionedNodeBuilder permissionedNodeBuilder;
  protected final PermissioningTransactions permissioningTransactions;
  protected final ContractTransactions contractTransactions;
  protected final Web3 web3;

  protected AcceptanceTestBase() {
    ethTransactions = new EthTransactions();
    accounts = new Accounts(ethTransactions);
    blockchain = new Blockchain(ethTransactions);
    eth = new Eth(ethTransactions);
    cliqueTransactions = new CliqueTransactions();
    ibftTwoTransactions = new Ibft2Transactions();
    accountTransactions = new AccountTransactions(accounts);
    permissioningTransactions = new PermissioningTransactions();
    contractTransactions = new ContractTransactions();

    clique = new Clique(ethTransactions, cliqueTransactions);
    ibftTwo = new Ibft2(ibftTwoTransactions);
    login = new Login();
    net = new Net(new NetTransactions());
    cluster = new Cluster(net);
    perm = new Perm(permissioningTransactions);
    admin = new Admin();
    web3 = new Web3(new Web3Transactions());
    pantheon = new PantheonNodeFactory();
    contractVerifier = new ContractVerifier(accounts.getPrimaryBenefactor());
    permissionedNodeBuilder = new PermissionedNodeBuilder();
  }

  @After
  public void tearDownAcceptanceTestBase() {
    cluster.close();
  }
}
