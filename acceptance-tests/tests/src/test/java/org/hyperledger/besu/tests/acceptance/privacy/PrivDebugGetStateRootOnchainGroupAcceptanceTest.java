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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.besu.tests.web3j.privacy.OnChainPrivacyAcceptanceTestBase;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.testcontainers.containers.Network;

@RunWith(Parameterized.class)
public class PrivDebugGetStateRootOnchainGroupAcceptanceTest
    extends OnChainPrivacyAcceptanceTestBase {

  private final EnclaveType enclaveType;

  public PrivDebugGetStateRootOnchainGroupAcceptanceTest(final EnclaveType enclaveType) {
    this.enclaveType = enclaveType;
  }

  @Parameters(name = "{0}")
  public static Collection<EnclaveType> enclaveTypes() {
    return Arrays.stream(EnclaveType.values())
        .filter(enclaveType -> enclaveType != EnclaveType.NOOP)
        .collect(Collectors.toList());
  }

  private PrivacyNode aliceNode;
  private PrivacyNode bobNode;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    final Network containerNetwork = Network.newNetwork();
    final String privateGenesisJson =
        "{"
            + "  \"alloc\": {"
            + "    \"0x1000000000000000000000000000000000000001\": {"
            + "      \"code\": \"0x608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633fa4f2451461005c5780636057361d1461008757806367e404ce146100c2575b600080fd5b34801561006857600080fd5b50610071610119565b6040518082815260200191505060405180910390f35b34801561009357600080fd5b506100c0600480360360208110156100aa57600080fd5b8101908080359060200190929190505050610123565b005b3480156100ce57600080fd5b506100d76101d9565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6000600254905090565b7fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f53382604051808373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020018281526020019250505060405180910390a18060028190555033600160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050565b6000600160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690509056fea165627a7a72305820e74360c3d08936cb1747ad641729261ff5e83b6fc0d303d136e171f15f07d7740029\""
            + "    }"
            + "  }"
            + "}";

    aliceNode =
        privacyBesu.createOnChainPrivacyGroupEnabledMinerNode(
            "alice-node",
            PrivacyAccountResolver.ALICE,
            false,
            enclaveType,
            Optional.of(containerNetwork),
            privateGenesisJson);
    bobNode =
        privacyBesu.createOnChainPrivacyGroupEnabledNode(
            "bob-node",
            PrivacyAccountResolver.BOB,
            false,
            enclaveType,
            Optional.of(containerNetwork),
            privateGenesisJson);

    privacyCluster.start(aliceNode, bobNode);
  }

  @Test
  public void nodesInGroupShouldHaveSameStateRoot() {
    final String privacyGroupId = createOnChainPrivacyGroup(aliceNode, bobNode);

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

    final String privacyGroupId = createOnChainPrivacyGroup(aliceNode, bobNode);

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
    final String privacyGroupId = createOnChainPrivacyGroup(aliceNode, bobNode);

    final EventEmitter eventEmitter =
        aliceNode.execute(
            privateContractTransactions.loadSmartContractWithPrivacyGroupId(
                "0x1000000000000000000000000000000000000001",
                EventEmitter.class,
                aliceNode.getTransactionSigningKey(),
                aliceNode.getEnclaveKey(),
                privacyGroupId));

    eventEmitter.store(BigInteger.valueOf(42)).send();

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
