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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.contract.CallPrivateSmartContractFunction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.CreateOnChainPrivacyGroupTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.util.LogFilterJsonParameter;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.PermissioningTransactions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy.PrivacyRequestFactory;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.besu.tests.web3j.privacy.OnChainPrivacyAcceptanceTestBase;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Base64String;

public class OnChainMultiTenancyAcceptanceTest extends OnChainPrivacyAcceptanceTestBase {

  private static final String eventEmitterDeployed =
      "0x6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029";

  private static final PermissioningTransactions permissioningTransactions =
      new PermissioningTransactions();
  private static final long VALUE_SET = 10L;

  private PrivacyNode alice;
  private MultiTenancyPrivacyNode aliceMultiTenancyPrivacyNode;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createOnChainPrivacyGroupEnabledMinerNode(
            "node1", PrivacyAccountResolver.MULTI_TENANCY, Address.PRIVACY, true);
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

    final String alice1EnclaveKey = alice.getOrion().getPublicKeys().get(0);
    final String alice2EnclaveKey = alice.getOrion().getPublicKeys().get(1);
    final String alice3EnclaveKey = alice.getOrion().getPublicKeys().get(2);

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
    createOnChainPrivacyGroup(alice);
  }

  @Test
  public void createPrivacyWithAllTenants() {
    final MultiTenancyPrivacyGroup justAlice = new MultiTenancyPrivacyGroup();
    justAlice.addNodeWithTenants(
        aliceMultiTenancyPrivacyNode, aliceMultiTenancyPrivacyNode.getTenants());
    createOnChainPrivacyGroup(justAlice);
  }

  @Test
  public void noAccessWhenNotAMember() {
    final MultiTenancyPrivacyGroup twoTenantsFromAlice = new MultiTenancyPrivacyGroup();
    final List<String> tenants = aliceMultiTenancyPrivacyNode.getTenants();
    final String removedTenant = tenants.remove(tenants.size() - 1);
    twoTenantsFromAlice.addNodeWithTenants(aliceMultiTenancyPrivacyNode, tenants);
    final String privacyGroupId = createOnChainPrivacyGroup(twoTenantsFromAlice);

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
                POW_CHAIN_ID,
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
    assertThat(
            privacyNode
                .execute(
                    privacyTransactions.privGetCode(
                        privacyGroupId,
                        Address.fromHexString(eventEmitter.getContractAddress()),
                        "latest"))
                .toHexString())
        .isEqualTo(eventEmitterDeployed);

    // check that getting the transaction receipt does not work if you are not a member
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    privacyNode.verify(
        privateTransactionVerifier.noPrivateTransactionReceipt(
            transactionHash)); // // TODO: returning null because the RPC is using the enclave key

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

    //        THIS does not work because I cannot find out what the nonce is "Unable to determine
    // nonce for account in group"
    //        // check that sending a transaction does not work if you are not a member
    //
    // privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(multiTenancyPrivacyNode.getTokenForTenant(removedTenant));
    //        privacyNode.execute(
    //                privateContractTransactions.callSmartContractWithPrivacyGroupId(
    //                        eventEmitter.getContractAddress(),
    //                        eventEmitter.store(BigInteger.valueOf(10L)).encodeFunctionCall(),
    //                        privacyNode.getTransactionSigningKey(),
    //                        POW_CHAIN_ID,
    //                        tenant,
    //                        privacyGroupId));

    // check that a member can call the contract
    privacyNodeBesu.useAuthenticationTokenInHeaderForJsonRpc(
        multiTenancyPrivacyNode.getTokenForTenant(tenant));
    final CallPrivateSmartContractFunction storeTransaction =
        privateContractTransactions.callSmartContractWithPrivacyGroupId(
            eventEmitter.getContractAddress(),
            eventEmitter.store(BigInteger.valueOf(VALUE_SET)).encodeFunctionCall(),
            privacyNode.getTransactionSigningKey(),
            POW_CHAIN_ID,
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
    final List<PrivacyRequestFactory.OnChainPrivacyGroup> group =
        privacyNode.execute(
            privacyTransactions.findOnChainPrivacyGroup(Base64String.unwrapList(base64StringList)));
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
                    privacyTransactions.findOnChainPrivacyGroup(
                        Base64String.unwrapList(base64StringList))))
        .hasMessageContaining("Error finding onchain privacy group");

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

  @SuppressWarnings(value = "unchecked")
  @Test
  public void removedMemberCannotGetFilterChanges() {
    final MultiTenancyPrivacyGroup allTenantsFromAlice = new MultiTenancyPrivacyGroup();
    final List<String> tenants = aliceMultiTenancyPrivacyNode.getTenants();
    allTenantsFromAlice.addNodeWithTenants(aliceMultiTenancyPrivacyNode, tenants);
    final String privacyGroupId = createOnChainPrivacyGroup(allTenantsFromAlice);
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
                POW_CHAIN_ID,
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
            POW_CHAIN_ID,
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
            POW_CHAIN_ID,
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

  private String createOnChainPrivacyGroup(final MultiTenancyPrivacyGroup group) {
    final List<MultiTenancyPrivacyNode> multiTenancyPrivacyNodes = group.getPrivacyNodes();
    final MultiTenancyPrivacyNode groupCreatorMultiTenancyPrivacyNode =
        multiTenancyPrivacyNodes.get(0);
    final PrivacyNode groupCreatorNode = group.getGroupCreatingPrivacyNode();
    final String groupCreatorTenant = group.getGroupCreatingTenant();
    final List<String> members = group.getTenants();
    final String token = groupCreatorMultiTenancyPrivacyNode.getTokenForTenant(groupCreatorTenant);
    final CreateOnChainPrivacyGroupTransaction createTx =
        privacyTransactions.createOnChainPrivacyGroup(
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
          privacyNode.verify(onChainPrivacyGroupExists(privacyGroupId, base64StringList));
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

  //  @Test
  //  public void privGetPrivateTransactionSuccessShouldReturnExpectedPrivateTransaction()
  //      throws JsonProcessingException {
  //    final PrivateTransaction validSignedPrivateTransaction =
  //        getValidSignedPrivateTransaction(senderAddress);
  //
  //    receiveEnclaveStub(validSignedPrivateTransaction);
  //    retrievePrivacyGroupEnclaveStub();
  //    sendEnclaveStub(KEY1);
  //
  //    final Hash transactionHash =
  //        node.execute(
  //            privacyTransactions.sendRawTransaction(
  //                getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
  //    node.verify(priv.getSuccessfulTransactionReceipt(transactionHash));
  //    node.verify(priv.getPrivateTransaction(transactionHash, validSignedPrivateTransaction));
  //  }
  //
  //  @Test
  //  public void privCreatePrivacyGroupSuccessShouldReturnNewId() throws JsonProcessingException {
  //    createPrivacyGroupEnclaveStub();
  //
  //    node.verify(
  //        priv.createPrivacyGroup(
  //            List.of(KEY1, KEY2, KEY3), "GroupName", "Group description.", PRIVACY_GROUP_ID));
  //  }
  //
  //  @Test
  //  public void privDeletePrivacyGroupSuccessShouldReturnId() throws JsonProcessingException {
  //    retrievePrivacyGroupEnclaveStub();
  //    deletePrivacyGroupEnclaveStub();
  //
  //    node.verify(priv.deletePrivacyGroup(PRIVACY_GROUP_ID));
  //  }
  //
  //  @Test
  //  public void privFindPrivacyGroupSuccessShouldReturnExpectedGroupMembership()
  //      throws JsonProcessingException {
  //    final List<PrivacyGroup> groupMembership =
  //        List.of(
  //            testPrivacyGroup(singletonList(ENCLAVE_KEY), PrivacyGroup.Type.PANTHEON),
  //            testPrivacyGroup(singletonList(ENCLAVE_KEY), PrivacyGroup.Type.PANTHEON),
  //            testPrivacyGroup(singletonList(ENCLAVE_KEY), PrivacyGroup.Type.PANTHEON));
  //
  //    findPrivacyGroupEnclaveStub(groupMembership);
  //
  //    node.verify(priv.findPrivacyGroup(groupMembership.size(), ENCLAVE_KEY));
  //  }
  //
  //  @Test
  //  public void eeaSendRawTransactionSuccessShouldReturnPrivateTransactionHash()
  //      throws JsonProcessingException {
  //    final PrivateTransaction validSignedPrivateTransaction =
  //        getValidSignedPrivateTransaction(senderAddress);
  //
  //    retrievePrivacyGroupEnclaveStub();
  //    sendEnclaveStub(KEY1);
  //    receiveEnclaveStub(validSignedPrivateTransaction);
  //
  //    node.verify(
  //        priv.eeaSendRawTransaction(
  //            getRLPOutput(validSignedPrivateTransaction).encoded().toHexString()));
  //  }
  //
  //  @Test
  //  public void privGetTransactionCountSuccessShouldReturnExpectedTransactionCount()
  //      throws JsonProcessingException {
  //    final PrivateTransaction validSignedPrivateTransaction =
  //        getValidSignedPrivateTransaction(senderAddress);
  //    final String accountAddress = validSignedPrivateTransaction.getSender().toHexString();
  //    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);
  //
  //    retrievePrivacyGroupEnclaveStub();
  //    sendEnclaveStub(KEY1);
  //    receiveEnclaveStub(validSignedPrivateTransaction);
  //
  //    node.verify(priv.getTransactionCount(accountAddress, PRIVACY_GROUP_ID, 0));
  //    final Hash transactionReceipt =
  //        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));
  //
  //    node.verify(priv.getSuccessfulTransactionReceipt(transactionReceipt));
  //    node.verify(priv.getTransactionCount(accountAddress, PRIVACY_GROUP_ID, 1));
  //  }
  //
  //  @Test
  //  public void privDistributeRawTransactionSuccessShouldReturnEnclaveKey()
  //      throws JsonProcessingException {
  //    final String enclaveResponseKeyBytes = Bytes.wrap(Bytes.fromBase64String(KEY1)).toString();
  //
  //    retrievePrivacyGroupEnclaveStub();
  //    sendEnclaveStub(KEY1);
  //
  //    node.verify(
  //        priv.distributeRawTransaction(
  //
  // getRLPOutput(getValidSignedPrivateTransaction(senderAddress)).encoded().toHexString(),
  //            enclaveResponseKeyBytes));
  //  }
  //
  //  @Test
  //  public void privGetTransactionReceiptSuccessShouldReturnTransactionReceiptAfterMined()
  //      throws JsonProcessingException {
  //    final PrivateTransaction validSignedPrivateTransaction =
  //        getValidSignedPrivateTransaction(senderAddress);
  //    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);
  //
  //    retrievePrivacyGroupEnclaveStub();
  //    sendEnclaveStub(KEY1);
  //    receiveEnclaveStub(validSignedPrivateTransaction);
  //
  //    final Hash transactionReceipt =
  //        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));
  //
  //    node.verify(priv.getSuccessfulTransactionReceipt(transactionReceipt));
  //  }
  //
  //  @Test
  //  public void privGetEeaTransactionCountSuccessShouldReturnExpectedTransactionCount()
  //      throws JsonProcessingException {
  //    final PrivateTransaction validSignedPrivateTransaction =
  //        getValidSignedPrivateTransaction(senderAddress);
  //    final String accountAddress = validSignedPrivateTransaction.getSender().toHexString();
  //    final String senderAddressBase64 =
  // Base64.encode(Bytes.wrap(accountAddress.getBytes(UTF_8)));
  //    final BytesValueRLPOutput rlpOutput = getRLPOutput(validSignedPrivateTransaction);
  //    final List<PrivacyGroup> groupMembership =
  //        List.of(testPrivacyGroup(emptyList(), PrivacyGroup.Type.LEGACY));
  //
  //    retrievePrivacyGroupEnclaveStub();
  //    sendEnclaveStub(KEY1);
  //    receiveEnclaveStub(validSignedPrivateTransaction);
  //    findPrivacyGroupEnclaveStub(groupMembership);
  //
  //    node.verify(priv.getTransactionCount(accountAddress, PRIVACY_GROUP_ID, 0));
  //    final Hash transactionHash =
  //        node.execute(privacyTransactions.sendRawTransaction(rlpOutput.encoded().toHexString()));
  //
  //    node.verify(priv.getSuccessfulTransactionReceipt(transactionHash));
  //
  //    final String privateFrom = ENCLAVE_KEY;
  //    final String[] privateFor = {senderAddressBase64};
  //    node.verify(priv.getEeaTransactionCount(accountAddress, privateFrom, privateFor, 1));
  //  }
  //
  //  private void findPrivacyGroupEnclaveStub(final List<PrivacyGroup> groupMembership)
  //      throws JsonProcessingException {
  //    final String findGroupResponse = mapper.writeValueAsString(groupMembership);
  //    stubFor(post("/findPrivacyGroup").willReturn(ok(findGroupResponse)));
  //  }
  //
  //  private void createPrivacyGroupEnclaveStub() throws JsonProcessingException {
  //    final String createGroupResponse =
  //        mapper.writeValueAsString(testPrivacyGroup(emptyList(), PrivacyGroup.Type.PANTHEON));
  //    stubFor(post("/createPrivacyGroup").willReturn(ok(createGroupResponse)));
  //  }
  //
  //  private void deletePrivacyGroupEnclaveStub() throws JsonProcessingException {
  //    final String deleteGroupResponse = mapper.writeValueAsString(PRIVACY_GROUP_ID);
  //    stubFor(post("/deletePrivacyGroup").willReturn(ok(deleteGroupResponse)));
  //  }
  //
  //  private void retrievePrivacyGroupEnclaveStub() throws JsonProcessingException {
  //    final String retrieveGroupResponse =
  //        mapper.writeValueAsString(
  //            testPrivacyGroup(List.of(ENCLAVE_KEY), PrivacyGroup.Type.PANTHEON));
  //    stubFor(post("/retrievePrivacyGroup").willReturn(ok(retrieveGroupResponse)));
  //  }
  //
  //  private void sendEnclaveStub(final String testKey) throws JsonProcessingException {
  //    final String sendResponse = mapper.writeValueAsString(new SendResponse(testKey));
  //    stubFor(post("/send").willReturn(ok(sendResponse)));
  //  }
  //
  //  private void receiveEnclaveStub(final PrivateTransaction privTx) throws
  // JsonProcessingException {
  //    final BytesValueRLPOutput rlpOutput = getRLPOutputForReceiveResponse(privTx);
  //    final String senderKey = privTx.getPrivateFrom().toBase64String();
  //    final String receiveResponse =
  //        mapper.writeValueAsString(
  //            new ReceiveResponse(
  //                rlpOutput.encoded().toBase64String().getBytes(UTF_8), PRIVACY_GROUP_ID,
  // senderKey));
  //    stubFor(post("/receive").willReturn(ok(receiveResponse)));
  //  }
  //
  //  private BytesValueRLPOutput getRLPOutputForReceiveResponse(
  //      final PrivateTransaction privateTransaction) {
  //    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
  //    privateTransaction.writeTo(bvrlpo);
  //    return bvrlpo;
  //  }
  //
  //  private BytesValueRLPOutput getRLPOutput(final PrivateTransaction privateTransaction) {
  //    final BytesValueRLPOutput bvrlpo = new BytesValueRLPOutput();
  //    privateTransaction.writeTo(bvrlpo);
  //    return bvrlpo;
  //  }
  //
  //  private PrivacyGroup testPrivacyGroup(
  //      final List<String> groupMembers, final PrivacyGroup.Type groupType) {
  //    return new PrivacyGroup(PRIVACY_GROUP_ID, groupType, "test", "testGroup", groupMembers);
  //  }
  //
  //  private static PrivateTransaction getValidSignedPrivateTransaction(final Address
  // senderAddress) {
  //    return PrivateTransaction.builder()
  //        .nonce(0)
  //        .gasPrice(Wei.ZERO)
  //        .gasLimit(3000000)
  //        .to(null)
  //        .value(Wei.ZERO)
  //        .payload(Bytes.wrap(new byte[] {}))
  //        .sender(senderAddress)
  //        .chainId(BigInteger.valueOf(2018))
  //        .privateFrom(Bytes.fromBase64String(ENCLAVE_KEY))
  //        .restriction(Restriction.RESTRICTED)
  //        .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
  //        .signAndBuild(TEST_KEY);
  //  }
}
