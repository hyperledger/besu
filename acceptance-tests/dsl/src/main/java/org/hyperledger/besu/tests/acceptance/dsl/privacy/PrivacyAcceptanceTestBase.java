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
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.WaitUtils;
import org.hyperledger.besu.tests.acceptance.dsl.condition.eth.EthConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.net.NetConditions;
import org.hyperledger.besu.tests.acceptance.dsl.condition.priv.PrivConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.privacy.PrivacyNodeFactory;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.PrivateContractVerifier;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.PrivateTransactionVerifier;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.contract.PrivateContractTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.PrivacyTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.contract.ContractTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.net.NetTransactions;

import java.math.BigInteger;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public class PrivacyAcceptanceTestBase {
  @ClassRule public static final TemporaryFolder privacy = new TemporaryFolder();

  protected final PrivacyTransactions privacyTransactions;
  protected final PrivateContractVerifier privateContractVerifier;
  protected final PrivateTransactionVerifier privateTransactionVerifier;
  protected final PrivacyNodeFactory privacyBesu;
  protected final PrivateContractTransactions privateContractTransactions;
  protected final PrivConditions priv;
  protected final PrivacyCluster privacyCluster;
  protected final PrivacyAccountResolver privacyAccountResolver;
  protected final ContractTransactions contractTransactions;
  protected final NetConditions net;
  protected final EthTransactions ethTransactions;
  protected final EthConditions eth;
  private final Vertx vertx = Vertx.vertx();

  public PrivacyAcceptanceTestBase() {
    ethTransactions = new EthTransactions();
    net = new NetConditions(new NetTransactions());
    privacyTransactions = new PrivacyTransactions();
    privateContractVerifier = new PrivateContractVerifier();
    privateTransactionVerifier = new PrivateTransactionVerifier(privacyTransactions);
    privacyBesu = new PrivacyNodeFactory(vertx);
    privateContractTransactions = new PrivateContractTransactions();
    privacyCluster = new PrivacyCluster(net);
    privacyAccountResolver = new PrivacyAccountResolver();
    priv =
        new PrivConditions(
            new org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy
                .PrivacyTransactions());
    contractTransactions = new ContractTransactions();
    eth = new EthConditions(ethTransactions);
  }

  protected void waitForBlockHeight(final PrivacyNode node, final long blockchainHeight) {
    WaitUtils.waitFor(
        120,
        () ->
            assertThat(node.execute(ethTransactions.blockNumber()))
                .isGreaterThanOrEqualTo(BigInteger.valueOf(blockchainHeight)));
  }

  @After
  public void tearDownAcceptanceTestBase() {
    privacyCluster.close();
    vertx.close();
  }
}
