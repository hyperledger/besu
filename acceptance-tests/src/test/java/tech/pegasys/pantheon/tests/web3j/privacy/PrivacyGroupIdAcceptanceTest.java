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

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.CreatePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class PrivacyGroupIdAcceptanceTest extends PrivacyAcceptanceTestBase {
  private static final String CONTRACT_NAME = "Event Emmiter";
  private EventEmitterHarness eventEmitterHarness;
  private PrivacyGroup privacyGroup;

  @Before
  public void setUp() throws Exception {
    PrivacyNet privacyNet =
        PrivacyNet.builder(privacy, privacyPantheon, cluster, false)
            .addMinerNode("Alice")
            .addMinerNode("Bob")
            .addMinerNode("Charlie")
            .build();

    privacyNet.startPrivacyNet();

    Enclave enclave =
        new Enclave(privacyNet.getNode("Alice").getPrivacyParameters().getEnclaveUri());
    String[] addresses =
        privacyNet.getNodes().values().stream()
            .map(privacyNode -> privacyNode.orion.getPublicKeys())
            .flatMap(List::stream)
            .toArray(String[]::new);
    this.privacyGroup =
        enclave.createPrivacyGroup(
            new CreatePrivacyGroupRequest(
                addresses,
                privacyNet.getNode("Alice").orion.getPublicKeys().get(0),
                "testName",
                "testDesc"));

    eventEmitterHarness =
        new EventEmitterHarness(
            privateTransactionBuilder,
            privacyNet,
            privateTransactions,
            privateTransactionVerifier,
            eea);
  }

  @Test
  public void nodeCanDeployWithPrivacyGroupId() {
    eventEmitterHarness.deployWithPrivacyGroup(
        CONTRACT_NAME, "Alice", privacyGroup.getPrivacyGroupId(), "Alice", "Bob", "Charlie");
  }
}
