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
package org.hyperledger.besu.tests.web3j.privacy;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.besu.response.privacy.PrivCreatePrivacyGroup;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;

public class PrivacyGroupAcceptanceTest extends PrivacyAcceptanceTestBase {

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "node1", privacyAccountResolver.resolve(0));
    bob =
        privacyBesu.createPrivateTransactionEnabledNode("node2", privacyAccountResolver.resolve(1));
    charlie =
        privacyBesu.createPrivateTransactionEnabledNode("node3", privacyAccountResolver.resolve(2));
    privacyCluster.start(alice, bob, charlie);
  }

  @Test
  public void nodeCanCreatePrivacyGroup() {
    final PrivCreatePrivacyGroup privCreatePrivacyGroup =
        alice.execute(
            privacyTransactions.createPrivacyGroup(
                "myGroupName", "my group description", alice, bob));

    final PrivateTransactionReceipt privateTransactionReceipt =
        alice.execute(
            privacyTransactions.getPrivateTransactionReceipt(
                privCreatePrivacyGroup.getCreatePrivacyGroupResponse().getTransactionHash()));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            privCreatePrivacyGroup.getCreatePrivacyGroupResponse().getTransactionHash(),
            privateTransactionReceipt));

    //    final PrivacyGroup expected =
    //        new PrivacyGroup(
    //
    // privCreatePrivacyGroup.getCreatePrivacyGroupResponse().getPrivacyGroupId().toString(),
    //            PrivacyGroup.Type.PANTHEON,
    //            "myGroupName",
    //            "my group description",
    //            Base64String.wrapList(alice.getEnclaveKey(), bob.getEnclaveKey()));
    //
    //    alice.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
    //
    //    bob.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  }

  //  @Test
  //  public void nodeCanCreatePrivacyGroupWithoutName() {
  //    final String privacyGroupId =
  //        alice.execute(
  //            privacyTransactions.createPrivacyGroup(null, "my group description", alice, bob));
  //
  //    assertThat(privacyGroupId).isNotNull();
  //
  //    final PrivacyGroup expected =
  //        new PrivacyGroup(
  //            privacyGroupId,
  //            PrivacyGroup.Type.PANTHEON,
  //            "",
  //            "my group description",
  //            Base64String.wrapList(alice.getEnclaveKey(), bob.getEnclaveKey()));
  //
  //    alice.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  //
  //    bob.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  //  }
  //
  //  @Test
  //  public void nodeCanCreatePrivacyGroupWithoutDescription() {
  //    final String privacyGroupId =
  //        alice.execute(privacyTransactions.createPrivacyGroup("myGroupName", null, alice, bob));
  //
  //    assertThat(privacyGroupId).isNotNull();
  //
  //    final PrivacyGroup expected =
  //        new PrivacyGroup(
  //            privacyGroupId,
  //            PrivacyGroup.Type.PANTHEON,
  //            "myGroupName",
  //            "",
  //            Base64String.wrapList(alice.getEnclaveKey(), bob.getEnclaveKey()));
  //
  //    alice.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  //
  //    bob.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  //  }
  //
  //  @Test
  //  public void nodeCanCreatePrivacyGroupWithoutOptionalParams() {
  //    final String privacyGroupId =
  //        alice.execute(privacyTransactions.createPrivacyGroup(null, null, alice, bob));
  //
  //    assertThat(privacyGroupId).isNotNull();
  //
  //    final PrivacyGroup expected =
  //        new PrivacyGroup(
  //            privacyGroupId,
  //            PrivacyGroup.Type.PANTHEON,
  //            "",
  //            "",
  //            Base64String.wrapList(alice.getEnclaveKey(), bob.getEnclaveKey()));
  //
  //    alice.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  //
  //    bob.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));
  //  }
}
