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

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.CreatePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;

import java.util.List;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

public class PrivacyGroupAcceptanceTest extends PrivacyAcceptanceTestBase {
  private static final String CONTRACT_NAME = "Event Emmiter";
  private EventEmitterHarness eventEmitterHarness;
  private PrivacyGroup privacyGroup;
  private PrivacyNet privacyNet;

  @Before
  public void setUp() throws Exception {
    privacyNet =
        PrivacyNet.builder(privacy, privacyPantheon, cluster, false)
            .addMinerNode("Alice")
            .addMinerNode("Bob")
            .addMinerNode("Charlie")
            .build();

    privacyNet.startPrivacyNet();

    final Enclave enclave =
        new Enclave(privacyNet.getNode("Alice").getPrivacyParameters().getEnclaveUri());
    final String[] addresses =
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

  @Test
  public void nodeCanCreatePrivacyGroup() {
    final String privacyGroupId =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.createPrivacyGroup(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0)),
                    "myGroupName",
                    "my group description"));

    assertThat(privacyGroupId).isNotNull();
    verifyGroupWasCreated();
    final List<PrivacyGroup> privacyGroups =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.findPrivacyGroup(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0))));

    assertThat(privacyGroups.size()).isEqualTo(1);
    assertThat(privacyGroups.get(0).getPrivacyGroupId()).isEqualTo(privacyGroupId);
    assertThat(privacyGroups.get(0).getName()).isEqualTo("myGroupName");
    assertThat(privacyGroups.get(0).getDescription()).isEqualTo("my group description");
    assertThat(privacyGroups.get(0).getMembers().length).isEqualTo(2);
  }

  @Test
  public void nodeCanCreatePrivacyGroupWithoutName() {
    final String privacyGroupId =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.createPrivacyGroupWithoutName(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0)),
                    "my group description"));

    assertThat(privacyGroupId).isNotNull();
    verifyGroupWasCreated();
    final List<PrivacyGroup> privacyGroups =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.findPrivacyGroup(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0))));

    assertThat(privacyGroups.size()).isEqualTo(1);
    assertThat(privacyGroups.get(0).getPrivacyGroupId()).isEqualTo(privacyGroupId);
    assertThat(privacyGroups.get(0).getName()).isEqualTo("Default Name");
    assertThat(privacyGroups.get(0).getDescription()).isEqualTo("my group description");
    assertThat(privacyGroups.get(0).getMembers().length).isEqualTo(2);
  }

  @Test
  public void nodeCanCreatePrivacyGroupWithoutDescription() {
    final String privacyGroupId =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.createPrivacyGroupWithoutDescription(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0)),
                    "myGroupName"));

    assertThat(privacyGroupId).isNotNull();
    verifyGroupWasCreated();
    final List<PrivacyGroup> privacyGroups =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.findPrivacyGroup(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0))));

    assertThat(privacyGroups.size()).isEqualTo(1);
    assertThat(privacyGroups.get(0).getPrivacyGroupId()).isEqualTo(privacyGroupId);
    assertThat(privacyGroups.get(0).getName()).isEqualTo("myGroupName");
    assertThat(privacyGroups.get(0).getDescription()).isEqualTo("Default Description");
    assertThat(privacyGroups.get(0).getMembers().length).isEqualTo(2);
  }

  @Test
  public void nodeCanCreatePrivacyGroupWithoutOptionalParams() {
    final String privacyGroupId =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.createPrivacyGroupWithoutOptionalParams(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0))));

    assertThat(privacyGroupId).isNotNull();
    verifyGroupWasCreated();
    final List<PrivacyGroup> privacyGroups =
        privacyNet
            .getNode("Alice")
            .execute(
                privateTransactions.findPrivacyGroup(
                    List.of(
                        privacyNet.getEnclave("Alice").getPublicKeys().get(0),
                        privacyNet.getEnclave("Bob").getPublicKeys().get(0))));

    assertThat(privacyGroups.size()).isEqualTo(1);
    assertThat(privacyGroups.get(0).getPrivacyGroupId()).isEqualTo(privacyGroupId);
    assertThat(privacyGroups.get(0).getName()).isEqualTo("Default Name");
    assertThat(privacyGroups.get(0).getDescription()).isEqualTo("Default Description");
    assertThat(privacyGroups.get(0).getMembers().length).isEqualTo(2);
  }

  private void verifyGroupWasCreated() {
    privacyNet
        .getNode("Alice")
        .verify(
            node ->
                Awaitility.await()
                    .until(
                        () ->
                            node.execute(
                                        privateTransactions.findPrivacyGroup(
                                            List.of(
                                                privacyNet
                                                    .getEnclave("Alice")
                                                    .getPublicKeys()
                                                    .get(0),
                                                privacyNet
                                                    .getEnclave("Bob")
                                                    .getPublicKeys()
                                                    .get(0))))
                                    .size()
                                > 0));
  }
}
