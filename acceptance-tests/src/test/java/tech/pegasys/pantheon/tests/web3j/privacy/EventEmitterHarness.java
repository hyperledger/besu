/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.tests.web3j.privacy;

import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;
import static tech.pegasys.pantheon.tests.web3j.privacy.PrivacyGroup.generatePrivacyGroup;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.eea.EeaCondition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.eea.EeaConditions;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateTransactionVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EventEmitterHarness {

  private PrivateTransactionBuilder.Builder privateTransactionBuilder;
  private PrivacyNet privacyNet;
  private PrivateTransactions privateTransactions;
  private PrivateTransactionVerifier privateTransactionVerifier;
  private EeaConditions eea;

  private Map<String, String> contracts;

  public EventEmitterHarness(
      final PrivateTransactionBuilder.Builder privateTransactionBuilder,
      final PrivacyNet privacyNet,
      final PrivateTransactions privateTransactions,
      final PrivateTransactionVerifier privateTransactionVerifier,
      final EeaConditions eea) {

    this.privateTransactionBuilder = privateTransactionBuilder;
    this.privacyNet = privacyNet;
    this.privateTransactions = privateTransactions;
    this.privateTransactionVerifier = privateTransactionVerifier;
    this.eea = eea;

    this.contracts = new HashMap<>();
  }

  public String resolveContractAddress(final String contractName) {
    return contracts.get(contractName);
  }

  public void deploy(final String contractName, final String sender, final String... receivers) {
    final BytesValue privacyGroupId = generatePrivacyGroup(privacyNet, sender, receivers);
    final long nonce = nextNonce(sender, privacyGroupId);
    final String contractAddress =
        generateContractAddress(sender, nonce, privacyGroupId).toString();
    deploy(
        contractName,
        privateTransactionVerifier.validPrivateContractDeployed(contractAddress),
        privateTransactionVerifier.noPrivateContractDeployed(),
        sender,
        receivers);
  }

  public void deployWithPrivacyGroup(
      final String contractName,
      final String sender,
      final String mPrivacyGroupId,
      final String... groupMembers) {
    deployWithPrivacyGroup(
        contractName,
        "Test",
        nextNonce(sender, BytesValues.fromBase64(mPrivacyGroupId)),
        sender,
        mPrivacyGroupId,
        groupMembers);
  }

  public void deploy(
      final String contractName,
      final EeaCondition forParticipants,
      final EeaCondition forNonParticipants,
      final String sender,
      final String... receivers) {
    final BytesValue privacyGroupId = generatePrivacyGroup(privacyNet, sender, receivers);
    final long nonce = nextNonce(sender, privacyGroupId);
    final String contractAddress =
        generateContractAddress(sender, nonce, privacyGroupId).toString();
    deploy(
        contractAddress,
        contractName,
        nonce,
        forParticipants,
        forNonParticipants,
        sender,
        receivers);
  }

  public void store(final String contractName, final String sender, final String... receivers) {
    store(
        contractName,
        privateTransactionVerifier.validEventReturned("1000"),
        privateTransactionVerifier.noValidEventReturned(),
        sender,
        receivers);
  }

  public void store(
      final String contractName,
      final EeaCondition forParticipants,
      final EeaCondition forNonParticipants,
      final String sender,
      final String... receivers) {
    final String contractAddress = resolveContractAddress(contractName);
    final BytesValue privacyGroupId = generatePrivacyGroup(privacyNet, sender, receivers);
    final long nonce = nextNonce(sender, privacyGroupId);
    final String storeValue =
        privateTransactionBuilder
            .nonce(nonce)
            .from(privacyNet.getNode(sender).getAddress())
            .to(Address.fromHexString(contractAddress))
            .privateFrom(
                BytesValues.fromBase64(privacyNet.getEnclave(sender).getPublicKeys().get(0)))
            .privateFor(convertNamesToOrionPublicKeys(receivers))
            .keyPair(privacyNet.getNode(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.STORE);
    final String transactionHash =
        privacyNet
            .getNode(sender)
            .execute(privateTransactions.createPrivateRawTransaction(storeValue));

    waitForTransactionToBeMined(transactionHash);

    verifyForParticipants(forParticipants, transactionHash, sender, receivers);

    verifyForNonParticipants(forNonParticipants, transactionHash, sender, receivers);
  }

  public void get(final String contractName, final String sender, final String... receivers) {
    get(
        contractName,
        privateTransactionVerifier.validOutputReturned("1000"),
        privateTransactionVerifier.noValidOutputReturned(),
        sender,
        receivers);
  }

  public void get(
      final String contractName,
      final EeaCondition forParticipants,
      final EeaCondition forNonParticipants,
      final String sender,
      final String... receivers) {
    final String contractAddress = resolveContractAddress(contractName);
    final BytesValue privacyGroupId = generatePrivacyGroup(privacyNet, sender, receivers);
    final long nonce = nextNonce(sender, privacyGroupId);
    final String getValue =
        privateTransactionBuilder
            .nonce(nonce)
            .from(privacyNet.getNode(sender).getAddress())
            .to(Address.fromHexString(contractAddress))
            .privateFrom(
                BytesValues.fromBase64(privacyNet.getEnclave(sender).getPublicKeys().get(0)))
            .privateFor(convertNamesToOrionPublicKeys(receivers))
            .keyPair(privacyNet.getNode(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.GET);
    final String transactionHash =
        privacyNet
            .getNode(sender)
            .execute(privateTransactions.createPrivateRawTransaction(getValue));

    waitForTransactionToBeMined(transactionHash);

    verifyForParticipants(forParticipants, transactionHash, sender, receivers);

    verifyForNonParticipants(forNonParticipants, transactionHash, sender, receivers);
  }

  private void deployWithPrivacyGroup(
      final String contractAddress,
      final String contractName,
      final long nonce,
      final String sender,
      final String privacyGroupId,
      final String... groupMembers) {

    final String deployContract =
        privateTransactionBuilder
            .nonce(nonce)
            .from(privacyNet.getNode(sender).getAddress())
            .to(null)
            .privateFrom(
                BytesValues.fromBase64(privacyNet.getEnclave(sender).getPublicKeys().get(0)))
            .privacyGroupId(BytesValues.fromBase64(privacyGroupId))
            .keyPair(privacyNet.getNode(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.CREATE_CONTRACT);
    final String transactionHash =
        privacyNet
            .getNode(sender)
            .execute(privateTransactions.deployPrivateSmartContract(deployContract));

    waitForTransactionToBeMined(transactionHash);

    verifyForParticipants(
        privateTransactionVerifier.validPrivateTransactionReceipt(),
        transactionHash,
        sender,
        groupMembers);

    contracts.put(contractName, contractAddress);
  }

  private void deploy(
      final String contractAddress,
      final String contractName,
      final long nonce,
      final EeaCondition forParticipants,
      final EeaCondition forNonParticipants,
      final String sender,
      final String... receivers) {
    final String deployContract =
        privateTransactionBuilder
            .nonce(nonce)
            .from(privacyNet.getNode(sender).getAddress())
            .to(null)
            .privateFrom(
                BytesValues.fromBase64(privacyNet.getEnclave(sender).getPublicKeys().get(0)))
            .privateFor(convertNamesToOrionPublicKeys(receivers))
            .keyPair(privacyNet.getNode(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.CREATE_CONTRACT);
    final String transactionHash =
        privacyNet
            .getNode(sender)
            .execute(privateTransactions.deployPrivateSmartContract(deployContract));

    waitForTransactionToBeMined(transactionHash);

    verifyForParticipants(forParticipants, transactionHash, sender, receivers);

    verifyForNonParticipants(forNonParticipants, transactionHash, sender, receivers);

    contracts.put(contractName, contractAddress);
  }

  private Address generateContractAddress(
      final String sender, final long nonce, final BytesValue privacyGroupId) {
    return Address.privateContractAddress(
        privacyNet.getNode(sender).getAddress(), nonce, privacyGroupId);
  }

  private long nextNonce(final String sender, final BytesValue privacyGroupId) {
    return privacyNet.getNode(sender).nextNonce(privacyGroupId);
  }

  private void waitForTransactionToBeMined(final String transactionHash) {
    waitFor(
        () ->
            privacyNet
                .getNode("Alice")
                .verify(eea.expectSuccessfulTransactionReceipt(transactionHash)));
  }

  private List<BytesValue> convertNamesToOrionPublicKeys(final String... toNodeNames) {
    return Arrays.stream(toNodeNames)
        .map(name -> BytesValues.fromBase64(privacyNet.getEnclave(name).getPublicKeys().get(0)))
        .collect(Collectors.toList());
  }

  private void verifyForNonParticipants(
      final EeaCondition condition,
      final String transactionHash,
      final String sender,
      final String[] receivers) {
    privacyNet.getNodes().keySet().stream()
        .filter(key -> !sender.equals(key) && !Arrays.asList(receivers).contains(key))
        .forEach(node -> verifyForParticipant(condition, transactionHash, node));
  }

  private void verifyForParticipants(
      final EeaCondition condition,
      final String transactionHash,
      final String fromNodeName,
      final String[] toNodeNames) {
    verifyForParticipant(condition, transactionHash, fromNodeName);
    Arrays.stream(toNodeNames)
        .forEach(node -> verifyForParticipant(condition, transactionHash, node));
  }

  private void verifyForParticipant(
      final EeaCondition condition, final String transactionHash, final String nodeName) {
    condition.verify(privacyNet.getNode(nodeName), transactionHash);
  }
}
