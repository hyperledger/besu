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
package tech.pegasys.pantheon.tests.web3j.privacy;

import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeployPrivateSmartContractAcceptanceTest extends PrivacyAcceptanceTestBase {

  protected static final String CONTRACT_NAME = "Event Emitter";

  private EventEmitterHarness eventEmitterHarness;
  private PrivacyNet privacyNet;

  @Before
  public void setUp() throws Exception {
    privacyNet =
        PrivacyNet.builder(privacy, privacyPantheon, cluster, false).addMinerNode("Alice").build();
    privacyNet.startPrivacyNet();
    eventEmitterHarness =
        new EventEmitterHarness(
            privateTransactionBuilder,
            privacyNet,
            privateTransactions,
            privateTransactionVerifier,
            eea);
  }

  @Test
  public void deployingMustGiveValidReceipt() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice");
  }

  @Test
  public void privateSmartContractMustEmitEvents() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice");
    eventEmitterHarness.store(CONTRACT_NAME, "Alice");
  }

  @Test
  public void privateSmartContractMustReturnValues() {
    eventEmitterHarness.deploy(CONTRACT_NAME, "Alice");
    eventEmitterHarness.store(CONTRACT_NAME, "Alice");
    eventEmitterHarness.get(CONTRACT_NAME, "Alice");
  }

  @After
  public void tearDown() {
    privacyNet.stopPrivacyNet();
  }
}
