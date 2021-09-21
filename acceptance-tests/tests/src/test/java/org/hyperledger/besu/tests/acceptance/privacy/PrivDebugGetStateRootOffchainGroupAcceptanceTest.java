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
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.web3j.utils.Restriction;

public class PrivDebugGetStateRootOffchainGroupAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode aliceNode;
  private final PrivacyNode bobNode;

  public PrivDebugGetStateRootOffchainGroupAcceptanceTest(
      final Restriction restriction, final EnclaveType enclaveType) throws IOException {

    super(restriction, enclaveType);

    final Network containerNetwork = Network.newNetwork();

    aliceNode =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "alice-node",
            PrivacyAccountResolver.ALICE,
            false,
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            restriction == UNRESTRICTED,
            "0xAA");
    bobNode =
        privacyBesu.createIbft2NodePrivacyEnabled(
            "bob-node",
            PrivacyAccountResolver.BOB,
            false,
            enclaveType,
            Optional.of(containerNetwork),
            false,
            false,
            restriction == UNRESTRICTED,
            "0xBB");

    privacyCluster.start(aliceNode, bobNode);
  }

  @Test
  public void nodesInGroupShouldHaveSameStateRoot() {
    final String privacyGroupId =
        aliceNode.execute(
            createPrivacyGroup("testGroup", "A group for everyone", aliceNode, bobNode));

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
    if (restriction != UNRESTRICTED) {
      final PrivacyRequestFactory.DebugGetStateRoot aliceResult =
          aliceNode.execute(
              privacyTransactions.debugGetStateRoot(
                  Hash.wrap(Bytes32.random()).toBase64String(), "latest"));

      assertThat(aliceResult.getResult()).isNull();
      assertThat(aliceResult.hasError()).isTrue();
      assertThat(aliceResult.getError()).isNotNull();
      assertThat(aliceResult.getError().getMessage()).contains("Error finding privacy group");
    }
  }
}
