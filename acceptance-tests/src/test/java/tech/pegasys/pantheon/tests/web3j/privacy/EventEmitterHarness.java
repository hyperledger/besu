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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.eea.EeaCondition;
import tech.pegasys.pantheon.tests.acceptance.dsl.jsonrpc.Eea;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivacyNet;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateTransactionVerifier;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.PrivateTransactions;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.PrivateTransactionBuilder;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Numeric;

public class EventEmitterHarness {

  private PrivateTransactionBuilder.Builder privateTransactionBuilder;
  private PrivacyNet privacyNet;
  private PrivateTransactions privateTransactions;
  private PrivateTransactionVerifier privateTransactionVerifier;
  private Eea eea;

  private Map<String, String> contracts;

  public EventEmitterHarness(
      final PrivateTransactionBuilder.Builder privateTransactionBuilder,
      final PrivacyNet privacyNet,
      final PrivateTransactions privateTransactions,
      final PrivateTransactionVerifier privateTransactionVerifier,
      final Eea eea) {

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
    final BytesValue privacyGroupId = generatePrivaycGroup(sender, receivers);
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

  public void deploy(
      final String contractName,
      final EeaCondition forParticipants,
      final EeaCondition forNonParticipants,
      final String sender,
      final String... receivers) {
    final BytesValue privacyGroupId = generatePrivaycGroup(sender, receivers);
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
    final BytesValue privacyGroupId = generatePrivaycGroup(sender, receivers);
    final long nonce = nextNonce(sender, privacyGroupId);
    final String storeValue =
        privateTransactionBuilder
            .nonce(nonce)
            .from(privacyNet.getPantheon(sender).getAddress())
            .to(Address.fromHexString(contractAddress))
            .privateFrom(
                BytesValue.wrap(
                    privacyNet.getEnclave(sender).getPublicKeys().get(0).getBytes(UTF_8)))
            .privateFor(convertNamesToOrionPublicKeys(receivers))
            .keyPair(privacyNet.getPantheon(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.STORE);
    final String transactionHash =
        privacyNet
            .getPantheon(sender)
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
    final BytesValue privacyGroupId = generatePrivaycGroup(sender, receivers);
    final long nonce = nextNonce(sender, privacyGroupId);
    final String getValue =
        privateTransactionBuilder
            .nonce(nonce)
            .from(privacyNet.getPantheon(sender).getAddress())
            .to(Address.fromHexString(contractAddress))
            .privateFrom(
                BytesValue.wrap(
                    privacyNet.getEnclave(sender).getPublicKeys().get(0).getBytes(UTF_8)))
            .privateFor(convertNamesToOrionPublicKeys(receivers))
            .keyPair(privacyNet.getPantheon(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.GET);
    final String transactionHash =
        privacyNet
            .getPantheon(sender)
            .execute(privateTransactions.createPrivateRawTransaction(getValue));

    waitForTransactionToBeMined(transactionHash);

    verifyForParticipants(forParticipants, transactionHash, sender, receivers);

    verifyForNonParticipants(forNonParticipants, transactionHash, sender, receivers);
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
            .from(privacyNet.getPantheon(sender).getAddress())
            .to(null)
            .privateFrom(
                BytesValue.wrap(
                    privacyNet.getEnclave(sender).getPublicKeys().get(0).getBytes(UTF_8)))
            .privateFor(convertNamesToOrionPublicKeys(receivers))
            .keyPair(privacyNet.getPantheon(sender).keyPair())
            .build(PrivateTransactionBuilder.TransactionType.CREATE_CONTRACT);
    final String transactionHash =
        privacyNet
            .getPantheon(sender)
            .execute(privateTransactions.deployPrivateSmartContract(deployContract));

    waitForTransactionToBeMined(transactionHash);

    verifyForParticipants(forParticipants, transactionHash, sender, receivers);

    verifyForNonParticipants(forNonParticipants, transactionHash, sender, receivers);

    contracts.put(contractName, contractAddress);
  }

  private Address generateContractAddress(
      final String sender, final long nonce, final BytesValue privacyGroupId) {
    return Address.privateContractAddress(
        privacyNet.getPantheon(sender).getAddress(), nonce, privacyGroupId);
  }

  private BytesValue generatePrivaycGroup(final String sender, final String[] receivers) {
    final List<byte[]> stringList = new ArrayList<>();
    stringList.add(
        Base64.getDecoder().decode(privacyNet.getEnclave(sender).getPublicKeys().get(0)));
    Arrays.stream(receivers)
        .forEach(
            (receiver) ->
                stringList.add(
                    Base64.getDecoder()
                        .decode(privacyNet.getEnclave(receiver).getPublicKeys().get(0))));
    List<RlpType> rlpList =
        stringList.stream()
            .distinct()
            .sorted(Comparator.comparing(Arrays::hashCode))
            .map(RlpString::create)
            .collect(Collectors.toList());
    return BytesValue.fromHexString(
        Numeric.toHexString(
            Base64.getEncoder().encode(Hash.sha3(RlpEncoder.encode(new RlpList(rlpList))))));
  }

  private long nextNonce(final String sender, final BytesValue privacyGroupId) {
    return privacyNet
        .getPantheon(sender)
        .execute(
            privateTransactions.getTransactionCount(
                privacyNet.getPantheon(sender).getAddress().toString(), privacyGroupId.toString()))
        .longValue();
  }

  private void waitForTransactionToBeMined(final String transactionHash) {
    waitFor(
        () ->
            privacyNet
                .getPantheon("Alice")
                .verify(eea.expectSuccessfulTransactionReceipt(transactionHash)));
  }

  private List<BytesValue> convertNamesToOrionPublicKeys(final String... toNodeNames) {
    return Arrays.stream(toNodeNames)
        .map(
            name ->
                BytesValue.wrap(privacyNet.getEnclave(name).getPublicKeys().get(0).getBytes(UTF_8)))
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
        .forEach(node -> verifyForParticipant(condition, transactionHash, fromNodeName));
  }

  private void verifyForParticipant(
      final EeaCondition condition, final String transactionHash, final String nodeName) {
    condition.verify(privacyNet.getPantheon(nodeName), transactionHash);
  }
}
