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
package org.hyperledger.besu.tests.acceptance.privacy.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.privacy.contracts.generated.DefaultFlexiblePrivacyGroupManagementContract;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
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
  private DefaultFlexiblePrivacyGroupManagementContract defaultPrivacyGroupManagementContract;

  private static final String RAW_FIRST_PARTICIPANT = "0x5aa68ac0";
  private static final String RAW_ADD_PARTICIPANT =
      "0x965a25ef00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000202a8d9b56a0fe9cd94d60be4413bcb721d3a7be27ed8e28b3a6346df874ee141b";
  private static final String RAW_REMOVE_PARTICIPANT =
      "0x1f52a8ee000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000202a8d9b56a0fe9cd94d60be4413bcb721d3a7be27ed8e28b3a6346df874ee141b";
  private static final String RAW_LOCK = "0xf83d08ba";
  private static final String RAW_UNLOCK = "0xa69df4b5";
  private static final String RAW_CAN_EXECUTE = "0x78b90337";
  private static final String RAW_GET_VERSION = "0x0d8e6e2c";

  private BesuNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createMinerNode("node");
    cluster.start(minerNode);
    defaultPrivacyGroupManagementContract =
        minerNode.execute(
            contractTransactions.createSmartContract(
                DefaultFlexiblePrivacyGroupManagementContract.class));
  }

  @Test
  public void rlp() throws Exception {
    final String contractAddress = "0x42699a7612a82f1d9c36148af9c77354759b210b";
    assertThat(defaultPrivacyGroupManagementContract.isValid()).isEqualTo(true);
    contractVerifier
        .validTransactionReceipt(contractAddress)
        .verify(defaultPrivacyGroupManagementContract);
    // 0x0b0235be
    assertThat(defaultPrivacyGroupManagementContract.getParticipants().encodeFunctionCall())
        .isEqualTo(RAW_FIRST_PARTICIPANT);
    // 0xf744b089
    assertThat(
            defaultPrivacyGroupManagementContract
                .addParticipants(Collections.singletonList(secondParticipant.raw()))
                .encodeFunctionCall())
        .isEqualTo(RAW_ADD_PARTICIPANT);
    // 0xf744b089
    assertThat(
            defaultPrivacyGroupManagementContract
                .removeParticipant(secondParticipant.raw())
                .encodeFunctionCall())
        .isEqualTo(RAW_REMOVE_PARTICIPANT);
    assertThat(defaultPrivacyGroupManagementContract.lock().encodeFunctionCall())
        .isEqualTo(RAW_LOCK);
    assertThat(defaultPrivacyGroupManagementContract.unlock().encodeFunctionCall())
        .isEqualTo(RAW_UNLOCK);
    assertThat(defaultPrivacyGroupManagementContract.canExecute().encodeFunctionCall())
        .isEqualTo(RAW_CAN_EXECUTE);
    assertThat(defaultPrivacyGroupManagementContract.getVersion().encodeFunctionCall())
        .isEqualTo(RAW_GET_VERSION);
  }

  @Test
  public void canInitiallyAddParticipants() throws Exception {
    final RemoteFunctionCall<TransactionReceipt> transactionReceiptRemoteFunctionCall =
        defaultPrivacyGroupManagementContract.addParticipants(
            Arrays.asList(firstParticipant.raw(), secondParticipant.raw()));
    transactionReceiptRemoteFunctionCall.send();
    final List<byte[]> participants =
        defaultPrivacyGroupManagementContract.getParticipants().send();
    assertThat(participants.size()).isEqualTo(2);
    assertThat(firstParticipant.raw()).isEqualTo(participants.get(0));
    assertThat(secondParticipant.raw()).isEqualTo(participants.get(1));
  }

  @Test
  public void canRemoveParticipant() throws Exception {
    defaultPrivacyGroupManagementContract
        .addParticipants(Arrays.asList(firstParticipant.raw(), secondParticipant.raw()))
        .send();
    final List<byte[]> participants =
        defaultPrivacyGroupManagementContract.getParticipants().send();
    assertThat(participants.size()).isEqualTo(2);
    assertThat(firstParticipant.raw()).isEqualTo(participants.get(0));
    assertThat(secondParticipant.raw()).isEqualTo(participants.get(1));
    defaultPrivacyGroupManagementContract.removeParticipant(secondParticipant.raw()).send();
    final List<byte[]> participantsAfterRemove =
        defaultPrivacyGroupManagementContract.getParticipants().send();
    assertThat(participantsAfterRemove.size()).isEqualTo(1);
    assertThat(firstParticipant.raw()).isEqualTo(participantsAfterRemove.get(0));
  }

  @Test
  public void cannotAddToContractWhenNotLocked() throws Exception {
    defaultPrivacyGroupManagementContract
        .addParticipants(Collections.singletonList(thirdParticipant.raw()))
        .send();

    assertThatThrownBy(
            () ->
                defaultPrivacyGroupManagementContract
                    .addParticipants(Collections.singletonList(secondParticipant.raw()))
                    .send())
        .isInstanceOf(TransactionException.class);
  }

  @Test
  public void ensureContractIsLockedAfterDeploy() throws Exception {
    assertThat(defaultPrivacyGroupManagementContract.canExecute().send()).isFalse();
  }

  @Test
  public void ensurePrivacyGroupVersionIsAlwaysDifferent() throws Exception {
    defaultPrivacyGroupManagementContract
        .addParticipants(Collections.singletonList(secondParticipant.raw()))
        .send();
    final byte[] version1 = defaultPrivacyGroupManagementContract.getVersion().send();
    defaultPrivacyGroupManagementContract.lock().send();
    defaultPrivacyGroupManagementContract
        .addParticipants(Collections.singletonList(thirdParticipant.raw()))
        .send();
    final byte[] version2 = defaultPrivacyGroupManagementContract.getVersion().send();
    defaultPrivacyGroupManagementContract.removeParticipant(secondParticipant.raw()).send();
    final byte[] version3 = defaultPrivacyGroupManagementContract.getVersion().send();

    assertThat(version1).isNotEqualTo(version2);
    assertThat(version1).isNotEqualTo(version3);
    assertThat(version2).isNotEqualTo(version3);
  }

  @Test
  public void canAddTwiceToContractWhenCallLock() throws Exception {
    defaultPrivacyGroupManagementContract
        .addParticipants(Arrays.asList(firstParticipant.raw(), thirdParticipant.raw()))
        .send();
    defaultPrivacyGroupManagementContract.lock().send();
    defaultPrivacyGroupManagementContract
        .addParticipants(Collections.singletonList(secondParticipant.raw()))
        .send();

    final List<byte[]> participants =
        defaultPrivacyGroupManagementContract.getParticipants().send();
    assertThat(participants.size()).isEqualTo(3);
    assertThat(firstParticipant.raw()).isEqualTo(participants.get(0));
    assertThat(thirdParticipant.raw()).isEqualTo(participants.get(1));
    assertThat(secondParticipant.raw()).isEqualTo(participants.get(2));
  }

  @Test
  public void cannotLockTwice() throws Exception {
    defaultPrivacyGroupManagementContract
        .addParticipants(Collections.singletonList(thirdParticipant.raw()))
        .send();
    defaultPrivacyGroupManagementContract.lock().send();
    assertThatThrownBy(() -> defaultPrivacyGroupManagementContract.lock().send())
        .isInstanceOf(TransactionException.class);
  }
}
