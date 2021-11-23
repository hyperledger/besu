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
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.ExpectValidFlexiblePrivacyGroupCreated;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.CreateFlexiblePrivacyGroupTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.utils.Base64String;

public class FlexiblePrivacyAcceptanceTestBase extends PrivacyAcceptanceTestBase {

  protected String createFlexiblePrivacyGroup(final PrivacyNode... members) {
    final List<String> addresses =
        Arrays.stream(members).map(PrivacyNode::getEnclaveKey).collect(Collectors.toList());
    return createFlexiblePrivacyGroup(members[0].getEnclaveKey(), addresses, members);
  }

  /**
   * Create an flexible privacy group. The privacy group id will be randomly generated.
   *
   * <p>This method also checks that each node member has successfully processed the transaction and
   * has the expected list of member for the group.
   *
   * @param members the list of members of the privacy group. The first member of the list will be
   *     the creator of the group.
   * @return the id of the privacy group
   */
  protected String createFlexiblePrivacyGroup(
      final String privateFrom, final List<String> addresses, final PrivacyNode... members) {

    final PrivacyNode groupCreator = members[0];

    final CreateFlexiblePrivacyGroupTransaction createTx =
        privacyTransactions.createFlexiblePrivacyGroup(groupCreator, privateFrom, addresses);

    final PrivacyRequestFactory.PrivxCreatePrivacyGroupResponse createResponse =
        groupCreator.execute(createTx);
    final String privacyGroupId = createResponse.getPrivacyGroupId();

    final List<Base64String> membersEnclaveKeys =
        Arrays.stream(members)
            .map(m -> Base64String.wrap(m.getEnclaveKey()))
            .collect(Collectors.toList());

    for (final PrivacyNode member : members) {
      member.verify(flexiblePrivacyGroupExists(privacyGroupId, membersEnclaveKeys));
    }

    final String commitmentHash =
        callGetParticipantsMethodAndReturnCommitmentHash(privacyGroupId, groupCreator, privateFrom);
    final PrivateTransactionReceipt expectedReceipt =
        buildExpectedAddMemberTransactionReceipt(
            privacyGroupId, groupCreator, addresses.toArray(new String[] {}));

    for (final PrivacyNode member : members) {
      member.verify(
          privateTransactionVerifier.validPrivateTransactionReceipt(
              commitmentHash, expectedReceipt));
    }

    return privacyGroupId;
  }

  protected String callGetParticipantsMethodAndReturnCommitmentHash(
      final String privacyGroupId, final PrivacyNode groupCreator, final String privateFrom) {
    return groupCreator.execute(
        privateContractTransactions.callOnchainPermissioningSmartContract(
            FLEXIBLE_PRIVACY_PROXY.toHexString(),
            GET_PARTICIPANTS_METHOD_SIGNATURE.toString(),
            groupCreator.getTransactionSigningKey(),
            privateFrom,
            privacyGroupId));
  }

  protected PrivateTransactionReceipt buildExpectedAddMemberTransactionReceipt(
      final String privacyGroupId, final PrivacyNode groupCreator, final String[] members) {
    return buildExpectedAddMemberTransactionReceipt(
        privacyGroupId, groupCreator, groupCreator.getEnclaveKey(), members);
  }

  protected PrivateTransactionReceipt buildExpectedAddMemberTransactionReceipt(
      final String privacyGroupId,
      final PrivacyNode groupCreator,
      final String privateFrom,
      final String[] members) {
    final StringBuilder output = new StringBuilder();
    // hex prefix
    output.append("0x");
    // Dynamic array offset
    output.append("0000000000000000000000000000000000000000000000000000000000000020");
    // Length of the array (with padded zeros to the left)
    output.append(Quantity.longToPaddedHex(members.length, 32).substring(2));
    // Each member enclave key converted from Base64 to bytes
    for (final String member : members) {
      output.append(Bytes.fromBase64String(member).toUnprefixedHexString());
    }

    return new PrivateTransactionReceipt(
        null,
        groupCreator.getAddress().toHexString(),
        FLEXIBLE_PRIVACY_PROXY.toHexString(),
        output.toString(),
        Collections.emptyList(),
        null,
        null,
        privateFrom,
        null,
        privacyGroupId,
        "0x1",
        null);
  }

  protected ExpectValidFlexiblePrivacyGroupCreated flexiblePrivacyGroupExists(
      final String privacyGroupId, final List<Base64String> members) {
    return privateTransactionVerifier.flexiblePrivacyGroupExists(privacyGroupId, members);
  }

  protected String getContractDeploymentCommitmentHash(final Contract contract) {
    final Optional<TransactionReceipt> transactionReceipt = contract.getTransactionReceipt();
    assertThat(transactionReceipt).isPresent();
    final PrivateTransactionReceipt privateTransactionReceipt =
        (PrivateTransactionReceipt) transactionReceipt.get();
    return privateTransactionReceipt.getcommitmentHash();
  }

  /**
   * This method will check if a privacy group with the specified id and list of members exists.
   * Each one of the members node will be queried to ensure that they all have the same privacy
   * group in their private state.
   *
   * @param privacyGroupId the id of the privacy group
   * @param members the list of member in the privacy group
   */
  protected void checkFlexiblePrivacyGroupExists(
      final String privacyGroupId, final PrivacyNode... members) {
    final List<Base64String> membersEnclaveKeys =
        Arrays.stream(members)
            .map(PrivacyNode::getEnclaveKey)
            .map(Base64String::wrap)
            .collect(Collectors.toList());

    for (final PrivacyNode member : members) {
      member.verify(flexiblePrivacyGroupExists(privacyGroupId, membersEnclaveKeys));
    }
  }
}
