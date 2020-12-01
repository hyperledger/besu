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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class PrivacyGroupOffchainAcceptanceTest extends PrivacyAcceptanceTestBase {

  private PrivacyNode aliceNode;
  private PrivacyNode bobNode;

  @Before
  public void setUp() throws IOException, URISyntaxException {
    aliceNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "alice-node", PrivacyAccountResolver.ALICE);
    bobNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "bob-node", PrivacyAccountResolver.BOB);
    privacyCluster.start(aliceNode, bobNode);
  }

  @Test
  public void nodesInGroupShouldHaveSameStateRoot() {
    final String privacyGroupId =
        aliceNode.execute(
            privacyTransactions.createPrivacyGroup(
                "testGroup", "A group for everyone", aliceNode, bobNode));

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
  public void nodesNotInGroupShouldReturnInvalidParams() {
    final String privacyGroupId =
        aliceNode.execute(
            privacyTransactions.createPrivacyGroup("testGroup", "A group for Alice", aliceNode));

    final Hash aliceStateRootId =
        aliceNode
            .execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "latest"))
            .getResult();

    assertThat(aliceStateRootId).isNotNull();
    assertThat(aliceStateRootId).isInstanceOf(Hash.class);

    assertThatThrownBy(
            () -> bobNode.execute(privacyTransactions.debugGetStateRoot(privacyGroupId, "latest")))
        .isInstanceOf(ClientConnectionException.class)
        .hasMessageContaining("Invalid params");
  }
}
