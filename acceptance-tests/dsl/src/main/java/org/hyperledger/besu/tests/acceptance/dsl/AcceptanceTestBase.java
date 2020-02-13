/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.tests.acceptance.dsl;

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Blockchain;
import org.hyperledger.besu.tests.acceptance.dsl.condition.admin.AdminConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.clique.CliqueConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.eth.EthConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.ibft2.Ibft2Conditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.login.LoginConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.perm.PermissioningConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.priv.PrivConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.web3.Web3Conditions;
import org.hyperledger.besu.tests.acceptance.dsl.contract.ContractVerifier;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.permissioning.PermissionedNodeBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.AccountTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.admin.AdminTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.clique.CliqueTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.contract.ContractTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.ibft2.Ibft2Transactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.web3.Web3Transactions;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class AcceptanceTestBase {
  protected final Logger LOG = LogManager.getLogger();

  protected final Accounts accounts;
  protected final AccountTransactions accountTransactions;
  protected final AdminConditions admin;
  protected final AdminTransactions adminTransactions;
  protected final Blockchain blockchain;
  protected final CliqueConditions clique;
  protected final CliqueTransactions cliqueTransactions;
  protected final Cluster cluster;
  protected final ContractVerifier contractVerifier;
  protected final ContractTransactions contractTransactions;
  protected final EthConditions eth;
  protected final EthTransactions ethTransactions;
  protected final Ibft2Transactions ibftTwoTransactions;
  protected final Ibft2Conditions ibftTwo;
  protected final LoginConditions login;
  protected final NetConditions net;
  protected final BesuNodeFactory besu;
  protected final PermissioningConditions perm;
  protected final PermissionedNodeBuilder permissionedNodeBuilder;
  protected final PermissioningTransactions permissioningTransactions;
  protected final MinerTransactions minerTransactions;
  protected final Web3Conditions web3;
  protected final PrivConditions priv;
  protected final PrivacyTransactions privacyTransactions;

  protected AcceptanceTestBase() {
    ethTransactions = new EthTransactions();
    accounts = new Accounts(ethTransactions);
    adminTransactions = new AdminTransactions();
    cliqueTransactions = new CliqueTransactions();
    ibftTwoTransactions = new Ibft2Transactions();
    accountTransactions = new AccountTransactions(accounts);
    permissioningTransactions = new PermissioningTransactions();
    privacyTransactions = new PrivacyTransactions();
    contractTransactions = new ContractTransactions();
    minerTransactions = new MinerTransactions();

    blockchain = new Blockchain(ethTransactions);
    clique = new CliqueConditions(ethTransactions, cliqueTransactions);
    eth = new EthConditions(ethTransactions);
    ibftTwo = new Ibft2Conditions(ibftTwoTransactions);
    login = new LoginConditions();
    net = new NetConditions(new NetTransactions());
    cluster = new Cluster(net);
    perm = new PermissioningConditions(permissioningTransactions);
    priv = new PrivConditions(privacyTransactions);
    admin = new AdminConditions(adminTransactions);
    web3 = new Web3Conditions(new Web3Transactions());
    besu = new BesuNodeFactory();
    contractVerifier = new ContractVerifier(accounts.getPrimaryBenefactor());
    permissionedNodeBuilder = new PermissionedNodeBuilder();
  }

  static {
    System.setProperty("log4j2.isThreadContextMapInheritable", "true");
  }

  @Rule public final TestName name = new TestName();

  @Rule
  public TestWatcher log_eraser =
      new TestWatcher() {

        @Override
        protected void starting(final Description description) {
          ThreadContext.put("test", description.getMethodName());
          ThreadContext.put("class", description.getClassName());
          Thread.currentThread()
              .setUncaughtExceptionHandler(
                  (thread, error) ->
                      LOG.error(
                          "Uncaught exception in thread \"" + thread.getName() + "\"", error));
          Thread.setDefaultUncaughtExceptionHandler(
              (thread, error) ->
                  LOG.error("Uncaught exception in thread \"" + thread.getName() + "\"", error));
        }

        @Override
        protected void failed(final Throwable e, final Description description) {
          // add the result at the end of the log so it is self-sufficient
          LOG.error(
              "==========================================================================================");
          LOG.error("Test failed. Reported Throwable at the point of failure:", e);
        }

        @Override
        protected void succeeded(final Description description) {
          // if so configured, delete logs of successful tests
          if (!Boolean.getBoolean("acctests.keepLogs")) {
            String pathname =
                "build/acceptanceTestLogs/"
                    + description.getClassName()
                    + "."
                    + description.getMethodName()
                    + ".log";
            LOG.info("Test successful, deleting log at {}", pathname);
            File file = new File(pathname);
            file.delete();
          }
        }
      };

  @After
  public void tearDownAcceptanceTestBase() {
    cluster.close();
  }
}
