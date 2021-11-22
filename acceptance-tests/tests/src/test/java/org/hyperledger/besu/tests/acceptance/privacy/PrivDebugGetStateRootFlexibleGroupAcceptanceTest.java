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
package org.hyperledger.besu.tests.acceptance.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.testcontainers.containers.Network;

@RunWith(Parameterized.class)
public class PrivDebugGetStateRootFlexibleGroupAcceptanceTest
    extends FlexiblePrivacyAcceptanceTestBase {

  private final EnclaveType enclaveType;

  public PrivDebugGetStateRootFlexibleGroupAcceptanceTest(final EnclaveType enclaveType) {
    this.enclaveType = enclaveType;
  }

  @Parameters(name = "{0}")
  public static Collection<EnclaveType> enclaveTypes() {
    return EnclaveType.valuesForTests();
  }

  private PrivacyNode aliceNode;
  private PrivacyNode bobNode;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    final Network containerNetwork = Network.newNetwork();

    aliceNode =
        privacyBesu.createFlexiblePrivacyGroupEnabledMinerNode(
            "alice-node",
            PrivacyAccountResolver.ALICE,
            false,
            enclaveType,
            Optional.of(containerNetwork));
    bobNode =
        privacyBesu.createFlexiblePrivacyGroupEnabledNode(
            "bob-node",
            PrivacyAccountResolver.BOB,
            false,
            enclaveType,
            Optional.of(containerNetwork));

    privacyCluster.start(aliceNode, bobNode);
  }

  @Test
  public void nodesInGroupShouldHaveSameStateRoot() {
    final String privacyGroupId = createFlexiblePrivacyGroup(aliceNode, bobNode);

    final Hash aliceStateRootId =
        aliceNode
            .execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "latest"))
            .getResult();

    final Hash bobStateRootId =
        bobNode
            .execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "latest"))
            .getResult();

    assertThat(aliceStateRootId).isEqualTo(bobStateRootId);
  }

  @Test
  public void unknownGroupShouldReturnError() {
    final PrivacyRequestFactory.DebugGetStateRoot aliceResult =
        aliceNode.execute(
            privacyTransactions.debugGetStateRoot(
                Hash.wrap(Bytes32.random()).toBase64String(), "latest"));

    assertThat(aliceResult.getResult()).isNull();
    assertThat(aliceResult.hasError()).isTrue();
    assertThat(aliceResult.getError()).isNotNull();
    assertThat(aliceResult.getError().getMessage()).contains("Error finding privacy group");
  }

  @Test
  public void blockParamShouldBeApplied() {
    waitForBlockHeight(aliceNode, 2);
    waitForBlockHeight(bobNode, 2);

    final String privacyGroupId = createFlexiblePrivacyGroup(aliceNode, bobNode);

    waitForBlockHeight(aliceNode, 10);
    waitForBlockHeight(bobNode, 10);

    final Hash aliceResult1 =
        aliceNode.execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "1")).getResult();
    final Hash bobResultInt1 =
        bobNode.execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "1")).getResult();

    assertThat(aliceResult1).isEqualTo(bobResultInt1);

    final Hash aliceResultLatest =
        aliceNode
            .execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "latest"))
            .getResult();

    final Hash bobResultLatest =
        bobNode
            .execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "latest"))
            .getResult();

    assertThat(aliceResultLatest).isEqualTo(bobResultLatest);
    assertThat(aliceResult1).isNotEqualTo(aliceResultLatest);
  }

  @Test
  public void canInteractWithPrivateGenesisPreCompile() throws Exception {
    final String privacyGroupId = createFlexiblePrivacyGroup(aliceNode, bobNode);

    final EventEmitter eventEmitter =
        aliceNode.execute(
            privateContractTransactions.loadSmartContractWithPrivacyGroupId(
                "0x1000000000000000000000000000000000000001",
                EventEmitter.class,
                aliceNode.getTransactionSigningKey(),
                aliceNode.getEnclaveKey(),
                privacyGroupId));

    privateTransactionVerifier.existingPrivateTransactionReceipt(
        eventEmitter.store(BigInteger.valueOf(42)).send().getTransactionHash());

    final String aliceResponse =
        aliceNode
            .execute(
                privacyTransactions.privCall(
                    privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()))
            .getValue();

    assertThat(new BigInteger(aliceResponse.substring(2), 16))
        .isEqualByComparingTo(BigInteger.valueOf(42));

    final String bobResponse =
        bobNode
            .execute(
                privacyTransactions.privCall(
                    privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()))
            .getValue();

    assertThat(new BigInteger(bobResponse.substring(2), 16))
        .isEqualByComparingTo(BigInteger.valueOf(42));
  }
}
