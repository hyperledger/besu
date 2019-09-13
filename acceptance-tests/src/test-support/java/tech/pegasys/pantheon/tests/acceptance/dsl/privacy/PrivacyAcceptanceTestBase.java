/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import tech.pegasys.pantheon.tests.acceptance.dsl.condition.net.NetConditions;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.privacy.PrivacyNodeFactory;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.condition.PrivateContractVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.condition.PrivateTransactionVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.contract.PrivateContractTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.transaction.PrivacyTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.net.NetTransactions;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

public class PrivacyAcceptanceTestBase {
  @ClassRule public static final TemporaryFolder privacy = new TemporaryFolder();

  protected final PrivacyTransactions privacyTransactions;
  protected final PrivateContractVerifier privateContractVerifier;
  protected final PrivateTransactionVerifier privateTransactionVerifier;
  protected final PrivacyNodeFactory privacyPantheon;
  protected final PrivateContractTransactions privateContractTransactions;
  protected final PrivacyCluster privacyCluster;
  protected final PrivacyAccountResolver privacyAccountResolver;

  protected final NetConditions net;

  public PrivacyAcceptanceTestBase() {
    net = new NetConditions(new NetTransactions());
    privacyTransactions = new PrivacyTransactions();
    privateContractVerifier = new PrivateContractVerifier();
    privateTransactionVerifier = new PrivateTransactionVerifier(privacyTransactions);
    privacyPantheon = new PrivacyNodeFactory();
    privateContractTransactions = new PrivateContractTransactions();
    privacyCluster = new PrivacyCluster(net);
    privacyAccountResolver = new PrivacyAccountResolver();
  }

  @After
  public void tearDownAcceptanceTestBase() {
    privacyCluster.close();
  }
}
