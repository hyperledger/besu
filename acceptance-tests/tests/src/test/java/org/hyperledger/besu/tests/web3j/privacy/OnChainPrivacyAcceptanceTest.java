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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.condition.eth.EthConditions;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.ExpectValidOnChainPrivacyGroupCreated;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.CreateOnChainPrivacyGroupTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory.PrivxCreatePrivacyGroupResponse;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.utils.Base64String;

public class OnChainPrivacyAcceptanceTest extends PrivacyAcceptanceTestBase {

  private static final long POW_CHAIN_ID = 2018;

  private PrivacyNode alice;
  private PrivacyNode bob;
  private PrivacyNode charlie;

  private final MinerTransactions minerTransactions = new MinerTransactions();
  private final EthConditions ethConditions = new EthConditions(ethTransactions);

  private static final String EXPECTED_STORE_OUTPUT_DATA =
      "0x000000000000000000000000f17f52151ebef6c7334fad080c5704d77216b7320000000000000000000000000000000000000000000000000000000000000539";
  private static final String EXPECTED_STORE_EVENT_TOPIC =
      "0xc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5";

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createOnChainPrivacyGroupEnabledMinerNode(
            "node1", privacyAccountResolver.resolve(0), Address.PRIVACY);
    bob =
        privacyBesu.createOnChainPrivacyGroupEnabledNode(
            "node2", privacyAccountResolver.resolve(1), Address.PRIVACY);
    charlie =
        privacyBesu.createOnChainPrivacyGroupEnabledNode(
            "node3", privacyAccountResolver.resolve(2), Address.PRIVACY);
    privacyCluster.start(alice, bob, charlie);
  }

  @Test
  public void nodeCanCreatePrivacyGroup() {
    final String privacyGroupId = createOnChainPrivacyGroup(alice, bob);
    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob);
  }

  @Test
  public void deployingMustGiveValidReceipt() {
    final String privacyGroupId = createOnChainPrivacyGroup(alice, bob);
    final Contract eventEmitter = deployPrivateContract(EventEmitter.class, privacyGroupId, alice);
    final String commitmentHash = getContractDeploymentCommitmentHash(eventEmitter);

    alice.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(commitmentHash));
    bob.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(commitmentHash));
  }

  @Test
  public void canAddParticipantToGroup() {
    final String privacyGroupId = createOnChainPrivacyGroup(alice, bob);
    final Contract eventEmitter = deployPrivateContract(EventEmitter.class, privacyGroupId, alice);
    final String commitmentHash = getContractDeploymentCommitmentHash(eventEmitter);

    charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(commitmentHash));

    lockPrivacyGroup(privacyGroupId, alice);
    addMembersToPrivacyGroup(privacyGroupId, alice, charlie);
    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob, charlie);

    charlie.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(commitmentHash));
  }

  @Test
  public void memberCanBeAddedAfterBeingRemoved() {
    final String privacyGroupId = createOnChainPrivacyGroup(alice);

    checkOnChainPrivacyGroupExists(privacyGroupId, alice);

    lockPrivacyGroup(privacyGroupId, alice);
    addMembersToPrivacyGroup(privacyGroupId, alice, bob);

    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob);

    final EventEmitter eventEmitter =
        deployPrivateContract(EventEmitter.class, privacyGroupId, alice);

    final int valueSetWhileBobWasMember = 17;
    final PrivateTransactionReceipt receiptWhileBobMember =
        setEventEmitterValueAndCheck(
            Lists.newArrayList(alice, bob),
            privacyGroupId,
            eventEmitter,
            valueSetWhileBobWasMember);

    removeFromPrivacyGroup(privacyGroupId, alice, bob);

    checkOnChainPrivacyGroupExists(privacyGroupId, alice);

    final int valueSetWhileBobWas_NOT_aMember = 1337;
    final PrivateTransactionReceipt receiptWhileBobRemoved =
        setEventEmitterValueAndCheck(
            Lists.newArrayList(alice),
            privacyGroupId,
            eventEmitter,
            valueSetWhileBobWas_NOT_aMember);
    checkEmitterValue(
        Lists.newArrayList(bob),
        privacyGroupId,
        eventEmitter,
        valueSetWhileBobWasMember); // bob did not get the last transaction

    lockPrivacyGroup(privacyGroupId, alice);
    addMembersToPrivacyGroup(privacyGroupId, alice, bob);

    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob);

    checkEmitterValue(
        Lists.newArrayList(alice, bob),
        privacyGroupId,
        eventEmitter,
        valueSetWhileBobWas_NOT_aMember);

    PrivateTransactionReceipt receipt =
        bob.execute(
            privacyTransactions.getPrivateTransactionReceipt(
                receiptWhileBobRemoved.getcommitmentHash()));
    assertThat(receipt.getStatus()).isEqualTo("0x1");

    receipt =
        bob.execute(
            privacyTransactions.getPrivateTransactionReceipt(
                receiptWhileBobMember.getcommitmentHash()));
    assertThat(receipt.getStatus()).isEqualTo("0x1");
  }

  PrivateTransactionReceipt setEventEmitterValueAndCheck(
      final List<PrivacyNode> nodes,
      final String privacyGroupId,
      final EventEmitter eventEmitter,
      final int valueSetWhileBobWasMember) {
    final PrivateTransactionReceipt receiptWhileBobMember =
        setEventEmitterValue(nodes, privacyGroupId, eventEmitter, valueSetWhileBobWasMember);

    checkEmitterValue(nodes, privacyGroupId, eventEmitter, valueSetWhileBobWasMember);
    return receiptWhileBobMember;
  }

  private PrivateTransactionReceipt setEventEmitterValue(
      final List<PrivacyNode> nodes,
      final String privacyGroupId,
      final EventEmitter eventEmitter,
      final int value) {
    final PrivacyNode firstNode = nodes.get(0);
    final String txHash =
        firstNode.execute(
            privateContractTransactions.callOnChainPermissioningSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.store(BigInteger.valueOf(value)).encodeFunctionCall(),
                firstNode.getTransactionSigningKey(),
                POW_CHAIN_ID,
                firstNode.getEnclaveKey(),
                privacyGroupId));
    PrivateTransactionReceipt receipt = null;
    for (final PrivacyNode node : nodes) {
      receipt = node.execute(privacyTransactions.getPrivateTransactionReceipt(txHash));
      assertThat(receipt.getStatus()).isEqualTo("0x1");
    }
    return receipt;
  }

  private void checkEmitterValue(
      final List<PrivacyNode> nodes,
      final String privacyGroupId,
      final EventEmitter eventEmitter,
      final int valueWhileBobMember) {
    for (final PrivacyNode node : nodes) {
      final EthCall response =
          node.execute(
              privacyTransactions.privCall(
                  privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()));

      assertThat(new BigInteger(response.getValue().substring(2), 16))
          .isEqualByComparingTo(BigInteger.valueOf(valueWhileBobMember));
    }
  }

  @Test
  public void bobCanAddCharlieAfterBeingAddedByAlice() {
    final String privacyGroupId = createOnChainPrivacyGroup(alice);

    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                POW_CHAIN_ID,
                alice.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), alice.getAddress().toString())
        .verify(eventEmitter);

    final String aliceLockHash =
        alice.execute(privacyTransactions.privxLockPrivacyGroupAndCheck(privacyGroupId, alice));

    alice.execute(privacyTransactions.addToPrivacyGroup(privacyGroupId, alice, bob));

    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob);

    bob.execute(privacyTransactions.privxLockPrivacyGroupAndCheck(privacyGroupId, bob));

    alice.execute(minerTransactions.minerStop());

    alice.getBesu().verify(ethConditions.miningStatus(false));

    final BigInteger pendingTransactionFilterId =
        alice.execute(ethTransactions.newPendingTransactionsFilter());

    final String callHash =
        alice.execute(
            privateContractTransactions.callOnChainPermissioningSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.value().encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                POW_CHAIN_ID,
                alice.getEnclaveKey(),
                privacyGroupId));

    final String bobAddHash = addMembersToPrivacyGroup(privacyGroupId, bob, charlie);

    alice
        .getBesu()
        .verify(
            ethConditions.expectNewPendingTransactions(
                pendingTransactionFilterId, Arrays.asList(callHash, bobAddHash)));

    alice.execute(minerTransactions.minerStart());

    alice.getBesu().verify(ethConditions.miningStatus(true));

    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob, charlie);

    final Optional<TransactionReceipt> aliceAddReceipt =
        alice.execute(ethTransactions.getTransactionReceipt(bobAddHash));
    assertThat(aliceAddReceipt.get().getStatus())
        .isEqualTo("0x1"); // this means the PMT for the "add" succeeded which is what we expect

    final Optional<TransactionReceipt> alicePublicReceipt =
        alice.execute(ethTransactions.getTransactionReceipt(callHash));
    if (alicePublicReceipt.isPresent()) {
      assertThat(alicePublicReceipt.get().getBlockHash())
          .isEqualTo(
              aliceAddReceipt
                  .get()
                  .getBlockHash()); // ensure that "add" and "call" are in the same block
      assertThat(alicePublicReceipt.get().getStatus())
          .isEqualTo(
              "0x1"); // this means the PMT for the "call" succeeded which is what we expect because
      // it is in the same block as the "add" and there is no way to tell that this
      // will happen before the block is mined
    }

    final PrivateTransactionReceipt aliceReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(callHash));
    assertThat(aliceReceipt.getStatus())
        .isEqualTo(
            "0x0"); // this means the "call" failed which is what we expect because the group was
    // locked!
    final PrivateTransactionReceipt bobReceipt =
        alice.execute(privacyTransactions.getPrivateTransactionReceipt(callHash));
    assertThat(bobReceipt.getStatus())
        .isEqualTo(
            "0x0"); // this means the "call" failed which is what we expect because the group was
    // locked!

    // assert charlie can access private transaction information from before he was added
    final PrivateTransactionReceipt expectedAliceLockReceipt =
        new PrivateTransactionReceipt(
            null,
            alice.getAddress().toHexString(),
            Address.PRIVACY_PROXY.toHexString(),
            "0x",
            Collections.emptyList(),
            null,
            null,
            alice.getEnclaveKey(),
            null,
            privacyGroupId,
            "0x1",
            null);
    charlie.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            aliceLockHash, expectedAliceLockReceipt));

    final String aliceStoreHash =
        charlie.execute(
            privateContractTransactions.callOnChainPermissioningSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.store(BigInteger.valueOf(1337)).encodeFunctionCall(),
                charlie.getTransactionSigningKey(),
                POW_CHAIN_ID,
                charlie.getEnclaveKey(),
                privacyGroupId));

    final PrivateTransactionReceipt expectedStoreReceipt =
        new PrivateTransactionReceipt(
            null,
            charlie.getAddress().toHexString(),
            eventEmitter.getContractAddress(),
            "0x",
            Collections.singletonList(
                new Log(
                    false,
                    "0x0",
                    "0x0",
                    aliceStoreHash,
                    null,
                    null,
                    eventEmitter.getContractAddress(),
                    EXPECTED_STORE_OUTPUT_DATA,
                    null,
                    Collections.singletonList(EXPECTED_STORE_EVENT_TOPIC))),
            null,
            null,
            charlie.getEnclaveKey(),
            null,
            privacyGroupId,
            "0x1",
            null);

    alice.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            aliceStoreHash, expectedStoreReceipt));

    bob.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            aliceStoreHash, expectedStoreReceipt));

    charlie.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            aliceStoreHash, expectedStoreReceipt));

    removeFromPrivacyGroup(privacyGroupId, bob, charlie);

    checkOnChainPrivacyGroupExists(privacyGroupId, alice, bob);
  }

  @Test
  public void addMembersToTwoGroupsInTheSameBlock() throws InterruptedException {
    final String privacyGroupId1 = createOnChainPrivacyGroup(alice);
    final String privacyGroupId2 = createOnChainPrivacyGroup(bob);
    checkOnChainPrivacyGroupExists(privacyGroupId1, alice);
    checkOnChainPrivacyGroupExists(privacyGroupId2, bob);

    lockPrivacyGroup(privacyGroupId1, alice);
    lockPrivacyGroup(privacyGroupId2, bob);

    final BigInteger pendingTransactionFilterId =
        alice.execute(ethTransactions.newPendingTransactionsFilter());

    alice.execute(minerTransactions.minerStop());
    alice.getBesu().verify(ethConditions.miningStatus(false));

    final String aliceAddHash = addMembersToPrivacyGroup(privacyGroupId1, alice, charlie);
    final String bobAddHash = addMembersToPrivacyGroup(privacyGroupId2, bob, alice);

    alice
        .getBesu()
        .verify(
            ethConditions.expectNewPendingTransactions(
                pendingTransactionFilterId, Arrays.asList(aliceAddHash, bobAddHash)));

    alice.execute(minerTransactions.minerStart());

    checkOnChainPrivacyGroupExists(privacyGroupId1, alice, charlie);
    checkOnChainPrivacyGroupExists(privacyGroupId2, bob, alice);
  }

  private <T extends Contract> T deployPrivateContract(
      final Class<T> clazz, final String privacyGroupId, final PrivacyNode sender) {
    final T contract =
        sender.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                clazz,
                sender.getTransactionSigningKey(),
                POW_CHAIN_ID,
                sender.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(contract.getContractAddress(), sender.getAddress().toString())
        .verify(contract);

    return contract;
  }

  /**
   * Crete an onchain privacy group. The privacy group id will be randomly generated.
   *
   * <p>This method also checks that each node member has successfully processed the transaction and
   * has the expected list of member for the group.
   *
   * @param members the list of members of the privacy group. The first member of the list will be
   *     the creator of the group.
   * @return the id of the privacy group
   */
  private String createOnChainPrivacyGroup(final PrivacyNode... members) {
    final PrivacyNode groupCreator = members[0];

    final CreateOnChainPrivacyGroupTransaction createTx =
        privacyTransactions.createOnChainPrivacyGroup(groupCreator, members);

    final PrivxCreatePrivacyGroupResponse createResponse = groupCreator.execute(createTx);
    final String privacyGroupId = createResponse.getPrivacyGroupId();

    for (final PrivacyNode member : members) {
      member.verify(onChainPrivacyGroupExists(privacyGroupId, members));
    }

    final String commitmentHash = calculateAddParticipantPMTHash(privacyGroupId, groupCreator);
    final PrivateTransactionReceipt expectedReceipt =
        buildExpectedAddMemberTransactionReceipt(privacyGroupId, groupCreator, members);

    for (final PrivacyNode member : members) {
      member.verify(
          privateTransactionVerifier.validPrivateTransactionReceipt(
              commitmentHash, expectedReceipt));
    }

    return privacyGroupId;
  }

  private String addMembersToPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode nodeAddingMember,
      final PrivacyNode... newMembers) {
    return nodeAddingMember.execute(
        privacyTransactions.addToPrivacyGroup(privacyGroupId, nodeAddingMember, newMembers));
  }

  private String removeFromPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode nodeRemovingMember,
      final PrivacyNode memberBeingRemoved) {
    return nodeRemovingMember.execute(
        privacyTransactions.removeFromPrivacyGroup(
            privacyGroupId, nodeRemovingMember, memberBeingRemoved));
  }

  private String calculateAddParticipantPMTHash(
      final String privacyGroupId, final PrivacyNode groupCreator) {
    return groupCreator.execute(
        privateContractTransactions.callOnChainPermissioningSmartContract(
            Address.PRIVACY_PROXY.toHexString(),
            GET_PARTICIPANTS_METHOD_SIGNATURE
                + Bytes.fromBase64String(groupCreator.getEnclaveKey()).toUnprefixedHexString(),
            groupCreator.getTransactionSigningKey(),
            POW_CHAIN_ID,
            groupCreator.getEnclaveKey(),
            privacyGroupId));
  }

  private PrivateTransactionReceipt buildExpectedAddMemberTransactionReceipt(
      final String privacyGroupId, final PrivacyNode groupCreator, final PrivacyNode[] members) {
    final StringBuilder output = new StringBuilder();
    // hex prefix
    output.append("0x");
    // Dynamic array offset
    output.append("0000000000000000000000000000000000000000000000000000000000000020");
    // Length of the array (with padded zeros to the left)
    output.append(Quantity.longToPaddedHex(members.length, 32).substring(2));
    // Each member enclave key converted from Base64 to bytes
    for (final PrivacyNode member : members) {
      output.append(Bytes.fromBase64String(member.getEnclaveKey()).toUnprefixedHexString());
    }

    return new PrivateTransactionReceipt(
        null,
        groupCreator.getAddress().toHexString(),
        Address.PRIVACY_PROXY.toHexString(),
        output.toString(),
        Collections.emptyList(),
        null,
        null,
        groupCreator.getEnclaveKey(),
        null,
        privacyGroupId,
        "0x1",
        null);
  }

  /**
   * This method will check if a privacy group with the specified id and list of members exists.
   * Each one of the members node will be queried to ensure that they all have the same privacy
   * group in their private state.
   *
   * @param privacyGroupId the id of the privacy group
   * @param members the list of member in the privacy group
   */
  private void checkOnChainPrivacyGroupExists(
      final String privacyGroupId, final PrivacyNode... members) {
    for (final PrivacyNode member : members) {
      member.verify(onChainPrivacyGroupExists(privacyGroupId, members));
    }
  }

  /**
   * This method will send a transaction to lock the privacy group identified by the specified id.
   * This also checks if the lock transaction was successful (see {@link
   * org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory#privxLockPrivacyGroup(PrivacyNode,
   * Base64String)}
   *
   * @return the hash of the lock privacy group transaction
   */
  private String lockPrivacyGroup(final String privacyGroupId, final PrivacyNode member) {
    return member.execute(
        privacyTransactions.privxLockPrivacyGroupAndCheck(privacyGroupId, member));
  }

  private ExpectValidOnChainPrivacyGroupCreated onChainPrivacyGroupExists(
      final String privacyGroupId, final PrivacyNode... members) {
    return privateTransactionVerifier.onChainPrivacyGroupExists(privacyGroupId, members);
  }

  private String getContractDeploymentCommitmentHash(final Contract contract) {
    final Optional<TransactionReceipt> transactionReceipt = contract.getTransactionReceipt();
    assertThat(transactionReceipt).isPresent();
    final PrivateTransactionReceipt privateTransactionReceipt =
        (PrivateTransactionReceipt) transactionReceipt.get();
    return privateTransactionReceipt.getcommitmentHash();
  }
}
