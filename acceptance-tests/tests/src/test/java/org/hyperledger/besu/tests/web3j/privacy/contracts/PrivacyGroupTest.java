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
package org.hyperledger.besu.tests.web3j.privacy.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.privacy.contracts.generated.DefaultOnChainPrivacyGroupManagementContract;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.utils.Base64String;

@SuppressWarnings("unchecked")
public class PrivacyGroupTest extends AcceptanceTestBase {

  private final Base64String firstParticipant =
      Base64String.wrap("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
  private final Base64String secondParticipant =
      Base64String.wrap("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=");
  private final Base64String thirdParticipant =
      Base64String.wrap("Jo2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=");
  private DefaultOnChainPrivacyGroupManagementContract privacyGroup;

  private static final String RAW_FIRST_PARTICIPANT =
      "0x0b0235be035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486a";
  private static final String RAW_ADD_PARTICIPANT =
      "0xf744b089035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486a000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000012a8d9b56a0fe9cd94d60be4413bcb721d3a7be27ed8e28b3a6346df874ee141b";
  private static final String RAW_REMOVE_PARTICIPANT =
      "0x61544c91035695b4cc4b0941e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486a2a8d9b56a0fe9cd94d60be4413bcb721d3a7be27ed8e28b3a6346df874ee141b";
  private static final String RAW_LOCK = "0xf83d08ba";
  private static final String RAW_UNLOCK = "0xa69df4b5";
  private static final String RAW_CAN_EXECUTE = "0x78b90337";
  private static final String RAW_GET_VERSION = "0x0d8e6e2c";

  private BesuNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("node");
    cluster.start(minerNode);
    privacyGroup =
        minerNode.execute(
            contractTransactions.createSmartContract(
                DefaultOnChainPrivacyGroupManagementContract.class));
  }

  @Test
  public void rlp() throws Exception {
    final String contractAddress = "0x42699a7612a82f1d9c36148af9c77354759b210b";
    assertThat(privacyGroup.isValid()).isEqualTo(true);
    contractVerifier.validTransactionReceipt(contractAddress).verify(privacyGroup);
    // 0x0b0235be
    assertThat(RAW_FIRST_PARTICIPANT)
        .isEqualTo(privacyGroup.getParticipants(firstParticipant.raw()).encodeFunctionCall());
    // 0xf744b089
    assertThat(RAW_ADD_PARTICIPANT)
        .isEqualTo(
            privacyGroup
                .addParticipants(
                    firstParticipant.raw(), Collections.singletonList(secondParticipant.raw()))
                .encodeFunctionCall());
    // 0xf744b089
    assertThat(RAW_REMOVE_PARTICIPANT)
        .isEqualTo(
            privacyGroup
                .removeParticipant(firstParticipant.raw(), secondParticipant.raw())
                .encodeFunctionCall());
    assertThat(RAW_LOCK).isEqualTo(privacyGroup.lock().encodeFunctionCall());
    assertThat(RAW_UNLOCK).isEqualTo(privacyGroup.unlock().encodeFunctionCall());
    assertThat(RAW_CAN_EXECUTE).isEqualTo(privacyGroup.canExecute().encodeFunctionCall());
    assertThat(RAW_GET_VERSION).isEqualTo(privacyGroup.getVersion().encodeFunctionCall());
  }

  @Test
  public void canInitiallyAddParticipants() throws Exception {
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(secondParticipant.raw()))
        .send();
    final List<byte[]> participants = privacyGroup.getParticipants(firstParticipant.raw()).send();
    assertThat(participants.size()).isEqualTo(2);
    assertThat(firstParticipant.raw()).isEqualTo(participants.get(0));
    assertThat(secondParticipant.raw()).isEqualTo(participants.get(1));
  }

  @Test
  public void canRemoveParticipant() throws Exception {
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(secondParticipant.raw()))
        .send();
    final List<byte[]> participants = privacyGroup.getParticipants(firstParticipant.raw()).send();
    assertThat(participants.size()).isEqualTo(2);
    assertThat(firstParticipant.raw()).isEqualTo(participants.get(0));
    assertThat(secondParticipant.raw()).isEqualTo(participants.get(1));
    privacyGroup.removeParticipant(firstParticipant.raw(), secondParticipant.raw()).send();
    final List<byte[]> participantsAfterRemove =
        privacyGroup.getParticipants(firstParticipant.raw()).send();
    assertThat(participantsAfterRemove.size()).isEqualTo(1);
    assertThat(firstParticipant.raw()).isEqualTo(participantsAfterRemove.get(0));
  }

  @Test(expected = TransactionException.class)
  public void cannotAddToContractWhenNotLocked() throws Exception {
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(thirdParticipant.raw()))
        .send();

    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(secondParticipant.raw()))
        .send();
  }

  @Test
  public void ensureContractIsNotLockedAfterDeploy() throws Exception {
    privacyGroup.unlock().send();
    assertThat(privacyGroup.canExecute().send()).isTrue();
  }

  @Test
  public void ensurePrivacyGroupVersionIsAlwaysDifferent() throws Exception {
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(secondParticipant.raw()))
        .send();
    final byte[] version1 = privacyGroup.getVersion().send();
    privacyGroup.lock().send();
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(thirdParticipant.raw()))
        .send();
    final byte[] version2 = privacyGroup.getVersion().send();
    privacyGroup.lock().send();
    privacyGroup.removeParticipant(firstParticipant.raw(), secondParticipant.raw()).send();
    final byte[] version3 = privacyGroup.getVersion().send();

    assertThat(version1).isNotEqualTo(version2);
    assertThat(version1).isNotEqualTo(version3);
    assertThat(version2).isNotEqualTo(version3);
  }

  @Test
  public void canAddTwiceToContractWhenCallLock() throws Exception {
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(thirdParticipant.raw()))
        .send();
    privacyGroup.lock().send();
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(secondParticipant.raw()))
        .send();

    final List<byte[]> participants = privacyGroup.getParticipants(firstParticipant.raw()).send();
    assertThat(participants.size()).isEqualTo(3);
    assertThat(firstParticipant.raw()).isEqualTo(participants.get(0));
    assertThat(thirdParticipant.raw()).isEqualTo(participants.get(1));
    assertThat(secondParticipant.raw()).isEqualTo(participants.get(2));
  }

  @Test(expected = TransactionException.class)
  public void cannotLockTwice() throws Exception {
    privacyGroup
        .addParticipants(firstParticipant.raw(), Collections.singletonList(thirdParticipant.raw()))
        .send();
    privacyGroup.lock().send();
    privacyGroup.lock().send();
  }
}
