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
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.junit.runners.Parameterized.Parameters;

import org.hyperledger.besu.tests.acceptance.dsl.condition.eth.EthConditions;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.miner.MinerTransactions;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.Network;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

@RunWith(Parameterized.class)
public class FlexiblePrivacyAcceptanceTest extends FlexiblePrivacyAcceptanceTestBase {

  private final EnclaveType enclaveType;

  public FlexiblePrivacyAcceptanceTest(final EnclaveType enclaveType) {
    this.enclaveType = enclaveType;
  }

  @Parameters(name = "{0}")
  public static Collection<EnclaveType> enclaveTypes() {
    return EnclaveType.valuesForTests();
  }

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
    final Network containerNetwork = Network.newNetwork();

    alice =
        privacyBesu.createFlexiblePrivacyGroupEnabledMinerNode(
            "node1",
            privacyAccountResolver.resolve(0),
            false,
            enclaveType,
            Optional.of(containerNetwork));
    bob =
        privacyBesu.createFlexiblePrivacyGroupEnabledNode(
            "node2",
            privacyAccountResolver.resolve(1),
            false,
            enclaveType,
            Optional.of(containerNetwork));
    charlie =
        privacyBesu.createFlexiblePrivacyGroupEnabledNode(
            "node3",
            privacyAccountResolver.resolve(2),
            false,
            enclaveType,
            Optional.of(containerNetwork));
    privacyCluster.start(alice, bob, charlie);
  }

  @Test
  public void nodeCanCreatePrivacyGroup() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice);
    checkFlexiblePrivacyGroupExists(privacyGroupId, alice);
  }

  @Test
  public void deployingMustGiveValidReceipt() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice, bob);
    final Contract eventEmitter = deployPrivateContract(EventEmitter.class, privacyGroupId, alice);
    final String commitmentHash = getContractDeploymentCommitmentHash(eventEmitter);

    alice.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(commitmentHash));
    bob.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(commitmentHash));
  }

  @Test
  public void canAddParticipantToGroup() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice, bob);
    final Contract eventEmitter = deployPrivateContract(EventEmitter.class, privacyGroupId, alice);
    final String commitmentHash = getContractDeploymentCommitmentHash(eventEmitter);

    charlie.verify(privateTransactionVerifier.noPrivateTransactionReceipt(commitmentHash));

    final Credentials aliceCredentials = Credentials.create(alice.getTransactionSigningKey());
    lockPrivacyGroup(privacyGroupId, alice, aliceCredentials);
    addMembersToPrivacyGroup(privacyGroupId, alice, aliceCredentials, charlie);
    checkFlexiblePrivacyGroupExists(privacyGroupId, alice, bob, charlie);

    charlie.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(commitmentHash));
  }

  @Test
  public void removedMemberCannotSendTransactionToGroup() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice, bob);

    final String removeHash =
        removeFromPrivacyGroup(
            privacyGroupId, alice, Credentials.create(alice.getTransactionSigningKey()), bob);

    bob.verify(privateTransactionVerifier.existingPrivateTransactionReceipt(removeHash));

    assertThatThrownBy(() -> deployPrivateContract(EventEmitter.class, privacyGroupId, bob))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Flexible Privacy group does not exist.");
  }

  @Test
  public void canInteractWithPrivateGenesisPreCompile() throws Exception {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice, bob);

    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.loadSmartContractWithPrivacyGroupId(
                "0x1000000000000000000000000000000000000001",
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                privacyGroupId));

    privateTransactionVerifier.existingPrivateTransactionReceipt(
        eventEmitter.store(BigInteger.valueOf(42)).send().getTransactionHash());

    final String aliceResponse =
        alice
            .execute(
                privacyTransactions.privCall(
                    privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()))
            .getValue();

    assertThat(new BigInteger(aliceResponse.substring(2), 16))
        .isEqualByComparingTo(BigInteger.valueOf(42));

    final String bobResponse =
        bob.execute(
                privacyTransactions.privCall(
                    privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()))
            .getValue();

    assertThat(new BigInteger(bobResponse.substring(2), 16))
        .isEqualByComparingTo(BigInteger.valueOf(42));

    final String charlieResponse =
        charlie
            .execute(
                privacyTransactions.privCall(
                    privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()))
            .getValue();

    assertThat(charlieResponse).isEqualTo("0x");
  }

  @Test
  public void memberCanBeAddedAfterBeingRemoved() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice);

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice);

    lockPrivacyGroup(privacyGroupId, alice, Credentials.create(alice.getTransactionSigningKey()));
    addMembersToPrivacyGroup(
        privacyGroupId, alice, Credentials.create(alice.getTransactionSigningKey()), bob);

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice, bob);

    final EventEmitter eventEmitter =
        deployPrivateContract(EventEmitter.class, privacyGroupId, alice);

    final int valueSetWhileBobWasMember = 17;
    final PrivateTransactionReceipt receiptWhileBobMember =
        setEventEmitterValueAndCheck(
            Lists.newArrayList(alice, bob),
            privacyGroupId,
            eventEmitter,
            valueSetWhileBobWasMember);

    removeFromPrivacyGroup(
        privacyGroupId, alice, Credentials.create(alice.getTransactionSigningKey()), bob);

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice);

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

    lockPrivacyGroup(privacyGroupId, alice, Credentials.create(alice.getTransactionSigningKey()));
    addMembersToPrivacyGroup(
        privacyGroupId, alice, Credentials.create(alice.getTransactionSigningKey()), bob);

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice, bob);

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
      final int value) {
    final PrivateTransactionReceipt receiptWhileBobMember =
        setEventEmitterValue(nodes, privacyGroupId, eventEmitter, value);

    checkEmitterValue(nodes, privacyGroupId, eventEmitter, value);
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
            privateContractTransactions.callOnchainPermissioningSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.store(BigInteger.valueOf(value)).encodeFunctionCall(),
                firstNode.getTransactionSigningKey(),
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
      final int expectedValue) {
    for (final PrivacyNode node : nodes) {
      final EthCall response =
          node.execute(
              privacyTransactions.privCall(
                  privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()));

      assertThat(new BigInteger(response.getValue().substring(2), 16))
          .isEqualByComparingTo(BigInteger.valueOf(expectedValue));
    }
  }

  @Test
  public void bobCanAddCharlieAfterBeingAddedByAlice() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice);
    checkFlexiblePrivacyGroupExists(privacyGroupId, alice);
    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), alice.getAddress().toString())
        .verify(eventEmitter);

    final Credentials aliceCredentials = Credentials.create(alice.getTransactionSigningKey());
    final String aliceLockHash =
        alice.execute(
            privacyTransactions.privxLockPrivacyGroupAndCheck(
                privacyGroupId, alice, aliceCredentials));

    alice.execute(
        privacyTransactions.addToPrivacyGroup(privacyGroupId, alice, aliceCredentials, bob));

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice, bob);

    bob.execute(
        privacyTransactions.privxLockPrivacyGroupAndCheck(privacyGroupId, bob, aliceCredentials));

    alice.execute(minerTransactions.minerStop());

    alice.getBesu().verify(ethConditions.miningStatus(false));

    final BigInteger pendingTransactionFilterId =
        alice.execute(ethTransactions.newPendingTransactionsFilter());

    final String callHash =
        alice.execute(
            privateContractTransactions.callOnchainPermissioningSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.value().encodeFunctionCall(),
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                privacyGroupId));

    final String bobAddHash =
        addMembersToPrivacyGroup(privacyGroupId, bob, aliceCredentials, charlie);

    alice
        .getBesu()
        .verify(
            ethConditions.expectNewPendingTransactions(
                pendingTransactionFilterId, Arrays.asList(callHash, bobAddHash)));

    alice.execute(minerTransactions.minerStart());

    alice.getBesu().verify(ethConditions.miningStatus(true));

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice, bob, charlie);

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
            FLEXIBLE_PRIVACY_PROXY.toHexString(),
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
            privateContractTransactions.callOnchainPermissioningSmartContract(
                eventEmitter.getContractAddress(),
                eventEmitter.store(BigInteger.valueOf(1337)).encodeFunctionCall(),
                charlie.getTransactionSigningKey(),
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

    removeFromPrivacyGroup(privacyGroupId, bob, aliceCredentials, charlie);

    checkFlexiblePrivacyGroupExists(privacyGroupId, alice, bob);
  }

  @Test
  public void canOnlyCallProxyContractWhenGroupLocked() {
    final String privacyGroupId = createFlexiblePrivacyGroup(alice);
    checkFlexiblePrivacyGroupExists(privacyGroupId, alice);

    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), alice.getAddress().toString())
        .verify(eventEmitter);

    final Credentials aliceCredentials = Credentials.create(alice.getTransactionSigningKey());

    final Supplier<String> callContract =
        () -> {
          return alice.execute(
              privateContractTransactions.callOnchainPermissioningSmartContract(
                  eventEmitter.getContractAddress(),
                  eventEmitter.value().encodeFunctionCall(),
                  alice.getTransactionSigningKey(),
                  alice.getEnclaveKey(),
                  privacyGroupId));
        };

    final String lockHash =
        alice.execute(
            privacyTransactions.privxLockPrivacyGroupAndCheck(
                privacyGroupId, alice, aliceCredentials));

    final String callWhileLockedHash = callContract.get();

    final String unlockHash =
        alice.execute(
            privacyTransactions.privxUnlockPrivacyGroupAndCheck(
                privacyGroupId, alice, aliceCredentials));

    final String callAfterUnlockedHash = callContract.get();

    alice.execute(minerTransactions.minerStart());
    alice.getBesu().verify(ethConditions.miningStatus(true));

    final BiConsumer<String, String> assertThatTransactionReceiptIs =
        (String hash, String expectedResult) -> {
          final PrivateTransactionReceipt receipt =
              alice.execute(privacyTransactions.getPrivateTransactionReceipt(hash));
          assertThat(receipt.getStatus()).isEqualTo(expectedResult);
        };

    // when locking a group succeeds ...
    assertThatTransactionReceiptIs.accept(lockHash, "0x1");
    // ... calls to contracts fail ...
    assertThatTransactionReceiptIs.accept(callWhileLockedHash, "0x0");
    // ... but unlock the group works ...
    assertThatTransactionReceiptIs.accept(unlockHash, "0x1");
    // ... and afterwards we can call contracts again
    assertThatTransactionReceiptIs.accept(callAfterUnlockedHash, "0x1");
  }

  @Test
  public void addMembersToTwoGroupsInTheSameBlock() {
    final String privacyGroupId1 = createFlexiblePrivacyGroup(alice);
    final String privacyGroupId2 = createFlexiblePrivacyGroup(bob);
    checkFlexiblePrivacyGroupExists(privacyGroupId1, alice);
    checkFlexiblePrivacyGroupExists(privacyGroupId2, bob);

    lockPrivacyGroup(privacyGroupId1, alice, Credentials.create(alice.getTransactionSigningKey()));
    lockPrivacyGroup(privacyGroupId2, bob, Credentials.create(bob.getTransactionSigningKey()));

    final BigInteger pendingTransactionFilterId =
        alice.execute(ethTransactions.newPendingTransactionsFilter());

    alice.execute(minerTransactions.minerStop());
    alice.getBesu().verify(ethConditions.miningStatus(false));

    final String aliceAddHash =
        addMembersToPrivacyGroup(
            privacyGroupId1, alice, Credentials.create(alice.getTransactionSigningKey()), charlie);
    final String bobAddHash =
        addMembersToPrivacyGroup(
            privacyGroupId2, bob, Credentials.create(bob.getTransactionSigningKey()), alice);

    alice
        .getBesu()
        .verify(
            ethConditions.expectNewPendingTransactions(
                pendingTransactionFilterId, Arrays.asList(aliceAddHash, bobAddHash)));

    alice.execute(minerTransactions.minerStart());

    checkFlexiblePrivacyGroupExists(privacyGroupId1, alice, charlie);
    checkFlexiblePrivacyGroupExists(privacyGroupId2, bob, alice);
  }

  private <T extends Contract> T deployPrivateContract(
      final Class<T> clazz, final String privacyGroupId, final PrivacyNode sender) {
    final T contract =
        sender.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                clazz, sender.getTransactionSigningKey(), sender.getEnclaveKey(), privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(contract.getContractAddress(), sender.getAddress().toString())
        .verify(contract);

    return contract;
  }

  private String addMembersToPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode nodeAddingMember,
      final Credentials signer,
      final PrivacyNode... newMembers) {
    return nodeAddingMember.execute(
        privacyTransactions.addToPrivacyGroup(
            privacyGroupId, nodeAddingMember, signer, newMembers));
  }

  private String removeFromPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode nodeRemovingMember,
      final Credentials signer,
      final PrivacyNode memberBeingRemoved) {
    return nodeRemovingMember.execute(
        privacyTransactions.removeFromPrivacyGroup(
            privacyGroupId,
            nodeRemovingMember.getEnclaveKey(),
            signer,
            memberBeingRemoved.getEnclaveKey()));
  }

  /**
   * This method will send a transaction to lock the privacy group identified by the specified id.
   * This also checks if the lock transaction was successful.
   *
   * @return the hash of the lock privacy group transaction
   */
  private String lockPrivacyGroup(
      final String privacyGroupId, final PrivacyNode member, final Credentials signer) {
    return member.execute(
        privacyTransactions.privxLockPrivacyGroupAndCheck(privacyGroupId, member, signer));
  }
}
