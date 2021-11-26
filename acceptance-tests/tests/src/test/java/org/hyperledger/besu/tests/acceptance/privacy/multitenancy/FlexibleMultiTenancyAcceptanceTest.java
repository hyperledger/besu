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
package org.hyperledger.besu.tests.acceptance.privacy.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.contract.CallPrivateSmartContractFunction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.CreateFlexiblePrivacyGroupTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.util.LogFilterJsonParameter;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;
import org.hyperledger.besu.tests.acceptance.privacy.FlexiblePrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Base64String;
import org.web3j.utils.Restriction;

@RunWith(Parameterized.class)
public class FlexibleMultiTenancyAcceptanceTest extends FlexiblePrivacyAcceptanceTestBase {

  private final EnclaveType enclaveType;

  public FlexibleMultiTenancyAcceptanceTest(final EnclaveType enclaveType) {
    this.enclaveType = enclaveType;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<EnclaveType> enclaveTypes() {
    return EnclaveType.valuesForTests();
  }

  private static final PermissioningTransactions permissioningTransactions =
      new PermissioningTransactions();
  private static final long VALUE_SET = 10L;

  private PrivacyNode alice;
  private MultiTenancyPrivacyNode aliceMultiTenancyPrivacyNode;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createFlexiblePrivacyGroupEnabledMinerNode(
            "node1", PrivacyAccountResolver.MULTI_TENANCY, true, enclaveType, Optional.empty());
    final BesuNode aliceBesu = alice.getBesu();
    privacyCluster.startNodes(alice);
    final String alice1Token =
        aliceBesu.execute(permissioningTransactions.createSuccessfulLogin("user", "pegasys"));
    aliceBesu.useAuthenticationTokenInHeaderForJsonRpc(alice1Token);
    final String alice2Token =
        aliceBesu.execute(permissioningTransactions.createSuccessfulLogin("user2", "Password2"));
    final String alice3Token =
        aliceBesu.execute(permissioningTransactions.createSuccessfulLogin("user3", "Password3"));
    privacyCluster.awaitPeerCount(alice);

    final String alice1EnclaveKey = alice.getEnclave().getPublicKeys().get(0);
    final String alice2EnclaveKey = alice.getEnclave().getPublicKeys().get(1);
    final String alice3EnclaveKey = alice.getEnclave().getPublicKeys().get(2);

    aliceMultiTenancyPrivacyNode = new MultiTenancyPrivacyNode(alice);
    aliceMultiTenancyPrivacyNode
        .addTenantWithToken(alice1EnclaveKey, alice1Token)
        .addTenantWithToken(alice2EnclaveKey, alice2Token)
        .addTenantWithToken(alice3EnclaveKey, alice3Token);
  }

  @After
  public void tearDown() {
    privacyCluster.close();
  }

  @Test
  public void createPrivacyGroup() {
    createFlexiblePrivacyGroup(alice);
  }

  @Test
  public void createPrivacyGroupWithAllTenants() {
    final MultiTenancyPrivacyGroup privacyGroup = new MultiTenancyPrivacyGroup();
    privacyGroup.addNodeWithTenants(
        aliceMultiTenancyPrivacyNode, aliceMultiTenancyPrivacyNode.getTenants());
    createFlexiblePrivacyGroup(privacyGroup);
  }

  @Test
  public void noAccessWhenNotAMember() {
    final MultiTenancyPrivacyGroup twoTenantsFromAlice = new MultiTenancyPrivacyGroup();
    final List<String> tenants = aliceMultiTenancyPrivacyNode.getTenants();
    final String removedTenant = tenants.remove(tenants.size() - 1);
    twoTenantsFromAlice.addNodeWithTenants(aliceMultiTenancyPrivacyNode, tenants);
    final String privacyGroupId = createFlexiblePrivacyGroup(twoTenantsFromAlice);

    final MultiTenancyPrivacyNode multiTenancyPrivacyNode =
        twoTenantsFromAlice.getPrivacyNodes().get(0);
    final String tenant = tenants.get(0);
    final PrivacyNode privacyNode = multiTenancyPrivacyNode.getPrivacyNode();
    final BesuNode privacyNodeBesu = privacyNode.getBesu();
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final EventEmitter eventEmitter =
        privacyNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                privacyNode.getTransactionSigningKey(),
                tenant,
                privacyGroupId));

    final String transactionHash = getContractDeploymentCommitmentHash(eventEmitter);

    // check that a member can get the transaction receipt
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    privacyNode.verify(
        privateTransactionVerifier.validPrivateTransactionReceipt(
            transactionHash,
            (PrivateTransactionReceipt) eventEmitter.getTransactionReceipt().get()));
    final String actual =
        privacyNode
            .execute(
                privacyTransactions.privGetCode(
                    privacyGroupId,
                    Address.fromHexString(eventEmitter.getContractAddress()),
                    "latest"))
            .toHexString();
    assertThat(EventEmitter.BINARY).contains(actual.substring(2));

    // check that getting the transaction receipt does not work if you are not a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    privacyNode.verify(
        privateTransactionVerifier.noPrivateTransactionReceipt(
            transactionHash)); // returning null because the RPC is using the enclave key

    // check that getting the code of the event emitter does not work when you are not a member
    assertThatThrownBy(
            () ->
                privacyNode.execute(
                    privacyTransactions.privGetCode(
                        privacyGroupId,
                        Address.fromHexString(eventEmitter.getContractAddress()),
                        "latest")))
        .hasMessageContaining("Unauthorized");

    final LogFilterJsonParameter filterParameter =
        new LogFilterJsonParameter(
            "earliest",
            "latest",
            List.of(eventEmitter.getContractAddress()),
            Collections.emptyList(),
            null);

    // create a valid filter
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final String filterId =
        privacyNode.execute(privacyTransactions.newFilter(privacyGroupId, filterParameter));

    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final CallPrivateSmartContractFunction storeTransaction =
        privateContractTransactions.callSmartContractWithPrivacyGroupId(
            eventEmitter.getContractAddress(),
            eventEmitter.store(BigInteger.valueOf(VALUE_SET)).encodeFunctionCall(),
            privacyNode.getTransactionSigningKey(),
            Restriction.RESTRICTED,
            tenant,
            privacyGroupId);
    final String storeTransactionHash = privacyNode.execute(storeTransaction);

    privacyNode.execute(privacyTransactions.getPrivateTransactionReceipt(storeTransactionHash));

    // check that getting the filter changes works for a member
    assertThat(privacyNode.execute(privacyTransactions.getFilterChanges(privacyGroupId, filterId)))
        .hasSize(1);

    // check that getting the filter changes does not work if you are not a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    assertThatThrownBy(
            () ->
                privacyNode.execute(privacyTransactions.getFilterChanges(privacyGroupId, filterId)))
        .hasMessageContaining("Unauthorized");

    // check that getting the filter logs works for a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    assertThat(privacyNode.execute(privacyTransactions.getFilterLogs(privacyGroupId, filterId)))
        .hasSize(3); // create privacy group, deploy event emitter, store on event emitter

    // check that getting the filter logs does not work if you are not a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    assertThatThrownBy(
            () -> privacyNode.execute(privacyTransactions.getFilterLogs(privacyGroupId, filterId)))
        .hasMessageContaining("Unauthorized");

    // check that getting the logs works for a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    assertThat(
            privacyNode.execute(privacyTransactions.privGetLogs(privacyGroupId, filterParameter)))
        .hasSize(3); // create privacy group, deploy event emitter, store on event emitter

    // check that getting the logs does not work if you are not a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    assertThatThrownBy(
            () ->
                privacyNode.execute(
                    privacyTransactions.privGetLogs(privacyGroupId, filterParameter)))
        .hasMessageContaining("Unauthorized");

    final List<Base64String> base64StringList =
        tenants.stream().map(Base64String::wrap).collect(Collectors.toList());

    // check that a member can find the on-chain privacy group
    privacyNode
        .getBesu()
        .useAuthenticationTokenInHeaderForJsonRpc(
            multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final List<PrivacyRequestFactory.FlexiblePrivacyGroup> group =
        privacyNode.execute(
            privacyTransactions.findFlexiblePrivacyGroup(
                Base64String.unwrapList(base64StringList)));
    assertThat(group.size()).isEqualTo(1);
    assertThat(group.get(0).getMembers()).containsAll(base64StringList).hasSize(2);

    // check that when you are not a member you cannot find the privacy group
    privacyNode
        .getBesu()
        .useAuthenticationTokenInHeaderForJsonRpc(
            multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    assertThatThrownBy(
            () ->
                privacyNode.execute(
                    privacyTransactions.findFlexiblePrivacyGroup(
                        Base64String.unwrapList(base64StringList))))
        .hasMessageContaining("Error finding flexible privacy group");

    // check that a member can do a priv_call
    privacyNode
        .getBesu()
        .useAuthenticationTokenInHeaderForJsonRpc(
            multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final EthCall readValue =
        privacyNode.execute(
            privacyTransactions.privCall(
                privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()));
    assertThat(new BigInteger(readValue.getValue().substring(2), 16))
        .isEqualByComparingTo(BigInteger.valueOf(VALUE_SET));

    // check that when you are not a member you cannot do a priv_call
    privacyNode
        .getBesu()
        .useAuthenticationTokenInHeaderForJsonRpc(
            multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    assertThatThrownBy(
            () ->
                privacyNode.execute(
                    privacyTransactions.privCall(
                        privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall())))
        .hasMessageContaining("Unauthorized");

    // check that a member can do a priv_getTransaction
    privacyNode
        .getBesu()
        .useAuthenticationTokenInHeaderForJsonRpc(
            multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final PrivacyRequestFactory.GetPrivateTransactionResponse privTransaction =
        privacyNode.execute(privacyTransactions.privGetTransaction(storeTransactionHash));
    assertThat(privTransaction.getResult().getPrivacyGroupId()).isEqualTo(privacyGroupId);

    // check that when you are not a member you cannot do a priv_getTransaction
    privacyNode
        .getBesu()
        .useAuthenticationTokenInHeaderForJsonRpc(
            multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    assertThatThrownBy(
            () -> privacyNode.execute(privacyTransactions.privGetTransaction(storeTransactionHash)))
        .hasMessageContaining(
            "Expecting actual not to be null"); // TODO: returning null because the RPC is using the
    // enclave key
  }

  @Test
  public void removedMemberCannotGetFilterChanges() {
    final MultiTenancyPrivacyGroup allTenantsFromAlice = new MultiTenancyPrivacyGroup();
    final List<String> tenants = aliceMultiTenancyPrivacyNode.getTenants();
    allTenantsFromAlice.addNodeWithTenants(aliceMultiTenancyPrivacyNode, tenants);
    final String privacyGroupId = createFlexiblePrivacyGroup(allTenantsFromAlice);
    final MultiTenancyPrivacyNode multiTenancyPrivacyNode =
        allTenantsFromAlice.getPrivacyNodes().get(0);
    final String groupCreatingTenant = allTenantsFromAlice.getGroupCreatingTenant();
    final String tenantToBeRemoved =
        tenants.stream().filter(t -> !t.equals(groupCreatingTenant)).findFirst().orElseThrow();
    final PrivacyNode groupCreatingPrivacyNode = allTenantsFromAlice.getGroupCreatingPrivacyNode();
    final BesuNode groupCreatingPrivacyNodeBesu = groupCreatingPrivacyNode.getBesu();
    groupCreatingPrivacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(groupCreatingTenant));

    final EventEmitter eventEmitter =
        groupCreatingPrivacyNode.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                groupCreatingPrivacyNode.getTransactionSigningKey(),
                groupCreatingTenant,
                privacyGroupId));

    final LogFilterJsonParameter filterParameter =
        new LogFilterJsonParameter(
            "earliest",
            "latest",
            List.of(eventEmitter.getContractAddress()),
            Collections.emptyList(),
            null);

    final String filterId =
        groupCreatingPrivacyNode.execute(
            privacyTransactions.newFilter(privacyGroupId, filterParameter));

    final CallPrivateSmartContractFunction storeTransaction =
        privateContractTransactions.callSmartContractWithPrivacyGroupId(
            eventEmitter.getContractAddress(),
            eventEmitter.store(BigInteger.valueOf(VALUE_SET)).encodeFunctionCall(),
            groupCreatingPrivacyNode.getTransactionSigningKey(),
            Restriction.RESTRICTED,
            groupCreatingTenant,
            privacyGroupId);
    final String storeTransactionHash = groupCreatingPrivacyNode.execute(storeTransaction);

    groupCreatingPrivacyNode.execute(
        privacyTransactions.getPrivateTransactionReceipt(storeTransactionHash));

    // check that getting the filter changes works for a member
    groupCreatingPrivacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenantToBeRemoved));

    assertThat(
            groupCreatingPrivacyNode.execute(
                privacyTransactions.getFilterChanges(privacyGroupId, filterId)))
        .hasSize(1);

    groupCreatingPrivacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(groupCreatingTenant));
    final CallPrivateSmartContractFunction store2Transaction =
        privateContractTransactions.callSmartContractWithPrivacyGroupId(
            eventEmitter.getContractAddress(),
            eventEmitter.store(BigInteger.valueOf(VALUE_SET)).encodeFunctionCall(),
            groupCreatingPrivacyNode.getTransactionSigningKey(),
            Restriction.RESTRICTED,
            groupCreatingTenant,
            privacyGroupId);
    final String store2TransactionHash = groupCreatingPrivacyNode.execute(store2Transaction);

    groupCreatingPrivacyNode.execute(
        privacyTransactions.getPrivateTransactionReceipt(store2TransactionHash));

    // now remove from privacy group
    final String removeTransactionHash =
        removeFromPrivacyGroup(
            privacyGroupId,
            groupCreatingPrivacyNode,
            groupCreatingTenant,
            Credentials.create(groupCreatingPrivacyNode.getTransactionSigningKey()),
            tenantToBeRemoved);
    groupCreatingPrivacyNode.execute(
        privacyTransactions.getPrivateTransactionReceipt(removeTransactionHash));

    // check that it does not work anymore when member has been removed
    groupCreatingPrivacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenantToBeRemoved));
    assertThatThrownBy(
            () ->
                groupCreatingPrivacyNode.execute(
                    privacyTransactions.getFilterChanges(privacyGroupId, filterId)))
        .hasMessageContaining("Unauthorized");
  }

  private String createFlexiblePrivacyGroup(final MultiTenancyPrivacyGroup group) {
    final List<MultiTenancyPrivacyNode> multiTenancyPrivacyNodes = group.getPrivacyNodes();
    final MultiTenancyPrivacyNode groupCreatorMultiTenancyPrivacyNode =
        multiTenancyPrivacyNodes.get(0);
    final PrivacyNode groupCreatorNode = group.getGroupCreatingPrivacyNode();
    final String groupCreatorTenant = group.getGroupCreatingTenant();
    final List<String> members = group.getTenants();
    final String token = groupCreatorMultiTenancyPrivacyNode.getTokenForTenant(groupCreatorTenant);
    final CreateFlexiblePrivacyGroupTransaction createTx =
        privacyTransactions.createFlexiblePrivacyGroup(
            groupCreatorNode, groupCreatorTenant, members, token);

    final PrivacyRequestFactory.PrivxCreatePrivacyGroupResponse createResponse =
        groupCreatorNode.execute(createTx);
    final String privacyGroupId = createResponse.getPrivacyGroupId();

    final List<Base64String> base64StringList =
        members.stream().map(Base64String::wrap).collect(Collectors.toList());
    for (final MultiTenancyPrivacyNode mtpn : multiTenancyPrivacyNodes) {
      final PrivacyNode privacyNode = mtpn.getPrivacyNode();
      for (final String tenant : mtpn.getTenants()) {
        if (members.contains(tenant)) {
          privacyNode
              .getBesu()
              .useAuthenticationTokenInHeaderForJsonRpc(mtpn.getTokenForTenant(tenant));
          privacyNode.verify(flexiblePrivacyGroupExists(privacyGroupId, base64StringList));
        }
      }
    }
    groupCreatorNode.getBesu().useAuthenticationTokenInHeaderForJsonRpc(token);
    final String commitmentHash =
        callGetParticipantsMethodAndReturnCommitmentHash(
            privacyGroupId, groupCreatorNode, groupCreatorTenant);
    final PrivateTransactionReceipt expectedReceipt =
        buildExpectedAddMemberTransactionReceipt(
            privacyGroupId, groupCreatorNode, groupCreatorTenant, members.toArray(new String[] {}));

    for (final MultiTenancyPrivacyNode mtpn : multiTenancyPrivacyNodes) {
      final PrivacyNode privacyNode = mtpn.getPrivacyNode();
      for (final String tenant : mtpn.getTenants()) {
        if (members.contains(tenant)) {
          privacyNode
              .getBesu()
              .useAuthenticationTokenInHeaderForJsonRpc(mtpn.getTokenForTenant(tenant));
          privacyNode.verify(
              privateTransactionVerifier.validPrivateTransactionReceipt(
                  commitmentHash, expectedReceipt));
        }
      }
    }

    return privacyGroupId;
  }

  private String removeFromPrivacyGroup(
      final String privacyGroupId,
      final PrivacyNode node,
      final String nodeRemovingMember,
      final Credentials signer,
      final String memberBeingRemoved) {
    return node.execute(
        privacyTransactions.removeFromPrivacyGroup(
            privacyGroupId, nodeRemovingMember, signer, memberBeingRemoved));
  }
}
