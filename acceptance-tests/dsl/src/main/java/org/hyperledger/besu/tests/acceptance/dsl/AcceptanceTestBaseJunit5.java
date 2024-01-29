/*
 * Copyright contributors to Hyperledger Besu.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Blockchain;
import org.hyperledger.besu.tests.acceptance.dsl.condition.admin.AdminConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.bft.BftConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.clique.CliqueConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.eth.EthConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.login.LoginConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.perm.PermissioningConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.priv.PrivConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.process.ExitedWithCode;
import org.hyperledger.besu.tests.acceptance.dsl.condition.txpool.TxPoolConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.web3.Web3Conditions;
import org.hyperledger.besu.tests.acceptance.dsl.contract.ContractVerifier;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.permissioning.PermissionedNodeBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.AccountTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.admin.AdminTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.bft.BftTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.clique.CliqueTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.contract.ContractTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.txpool.TxPoolTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.web3.Web3Transactions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superclass for acceptance tests. For now (transition to junit5 is ongoing) this class supports
 * junit5 format. Once the transition is complete, this class can be removed and recombined with
 * AcceptanceTestBase (original).
 */
@ExtendWith(AcceptanceTestBaseTestWatcher.class)
public class AcceptanceTestBaseJunit5 {

  private static final Logger LOG = LoggerFactory.getLogger(AcceptanceTestBaseJunit5.class);

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
  protected final BftTransactions bftTransactions;
  protected final BftConditions bft;
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
  protected final TxPoolConditions txPoolConditions;
  protected final TxPoolTransactions txPoolTransactions;
  protected final ExitedWithCode exitedSuccessfully;

  private final ExecutorService outputProcessorExecutor = Executors.newCachedThreadPool();

  protected AcceptanceTestBaseJunit5() {
    ethTransactions = new EthTransactions();
    accounts = new Accounts(ethTransactions);
    adminTransactions = new AdminTransactions();
    cliqueTransactions = new CliqueTransactions();
    bftTransactions = new BftTransactions();
    accountTransactions = new AccountTransactions(accounts);
    permissioningTransactions = new PermissioningTransactions();
    privacyTransactions = new PrivacyTransactions();
    contractTransactions = new ContractTransactions();
    minerTransactions = new MinerTransactions();

    blockchain = new Blockchain(ethTransactions);
    clique = new CliqueConditions(ethTransactions, cliqueTransactions);
    eth = new EthConditions(ethTransactions);
    bft = new BftConditions(bftTransactions);
    login = new LoginConditions();
    net = new NetConditions(new NetTransactions());
    cluster = new Cluster(net);
    perm = new PermissioningConditions(permissioningTransactions);
    priv = new PrivConditions(privacyTransactions);
    admin = new AdminConditions(adminTransactions);
    web3 = new Web3Conditions(new Web3Transactions());
    besu = new BesuNodeFactory();
    txPoolTransactions = new TxPoolTransactions();
    txPoolConditions = new TxPoolConditions(txPoolTransactions);
    contractVerifier = new ContractVerifier(accounts.getPrimaryBenefactor());
    permissionedNodeBuilder = new PermissionedNodeBuilder();
    exitedSuccessfully = new ExitedWithCode(0);
  }

  @BeforeEach
  public void setUp(final TestInfo testInfo) {
    // log4j is configured to create a file per test
    // build/acceptanceTestLogs/${ctx:class}.${ctx:test}.log
    ThreadContext.put("class", this.getClass().getSimpleName());
    ThreadContext.put("test", testInfo.getTestMethod().get().getName());
  }

  @AfterEach
  public void tearDownAcceptanceTestBase() {
    reportMemory();
    cluster.close();
  }

  public void reportMemory() {
    String os = System.getProperty("os.name");
    String[] command = null;
    if (os.contains("Linux")) {
      command = new String[] {"/usr/bin/top", "-n", "1", "-o", "%MEM", "-b", "-c", "-w", "180"};
    }
    if (os.contains("Mac")) {
      command = new String[] {"/usr/bin/top", "-l", "1", "-o", "mem", "-n", "20"};
    }
    if (command != null) {
      LOG.info("Memory usage at end of test:");
      final ProcessBuilder processBuilder =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectInput(ProcessBuilder.Redirect.INHERIT);
      try {
        final Process memInfoProcess = processBuilder.start();
        outputProcessorExecutor.execute(() -> printOutput(memInfoProcess));
        memInfoProcess.waitFor();
        LOG.debug("Memory info process exited with code {}", memInfoProcess.exitValue());
      } catch (final Exception e) {
        LOG.warn("Error running memory information process", e);
      }
    } else {
      LOG.info("Don't know how to report memory for OS {}", os);
    }
  }

  private void printOutput(final Process process) {
    try (final BufferedReader in =
        new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
      String line = in.readLine();
      while (line != null) {
        LOG.info(line);
        line = in.readLine();
      }
    } catch (final IOException e) {
      LOG.warn("Failed to read output from memory information process: ", e);
    }
  }

  protected void waitForBlockHeight(final Node node, final long blockchainHeight) {
    WaitUtils.waitFor(
        120,
        () ->
            assertThat(node.execute(ethTransactions.blockNumber()))
                .isGreaterThanOrEqualTo(BigInteger.valueOf(blockchainHeight)));
  }
}
