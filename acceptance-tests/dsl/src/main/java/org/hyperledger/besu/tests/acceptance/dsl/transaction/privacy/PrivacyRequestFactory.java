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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.privacy;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.parameters.CreatePrivacyGroupParameter;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivateTransactionGroupResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.Contract;
import org.web3j.tx.response.PollingPrivateTransactionReceiptProcessor;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;

public class PrivacyRequestFactory {

  private final SecureRandom secureRandom;

  public static class GetPrivacyPrecompileAddressResponse extends Response<Address> {}

  public static class GetPrivateTransactionResponse
      extends Response<PrivateTransactionGroupResponse> {}

  public static class CreatePrivacyGroupResponse extends Response<String> {}

  public static class DeletePrivacyGroupResponse extends Response<String> {}

  public static class FindPrivacyGroupResponse extends Response<PrivacyGroup[]> {}

  public static class SendRawTransactionResponse extends Response<Hash> {}

  public static class GetTransactionReceiptResponse extends Response<PrivateTransactionReceipt> {}

  public static class GetTransactionCountResponse extends Response<Integer> {

    final Integer count;

    @JsonCreator
    public GetTransactionCountResponse(@JsonProperty("result") final String result) {
      this.count = Integer.decode(result);
    }

    public Integer getCount() {
      return count;
    }
  }

  public static class GetCodeResponse extends Response<String> {}

  public Request<?, PrivDistributeTransactionResponse> privDistributeTransaction(
      final String signedPrivateTransaction) {
    return new Request<>(
        "priv_distributeRawTransaction",
        singletonList(signedPrivateTransaction),
        web3jService,
        PrivDistributeTransactionResponse.class);
  }

  private final Besu besuClient;
  private final Web3jService web3jService;

  public PrivacyRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
    this.besuClient = Besu.build(web3jService);
    this.secureRandom = SecureRandomProvider.createSecureRandom();
  }

  public Besu getBesuClient() {
    return besuClient;
  }

  public static class PrivDistributeTransactionResponse extends Response<String> {

    public PrivDistributeTransactionResponse() {}

    public String getTransactionKey() {
      return getResult();
    }
  }

  public String privxAddToPrivacyGroup(
      final Base64String privacyGroupId, final PrivacyNode adder, final List<String> addresses)
      throws IOException, TransactionException {

    final BigInteger nonce =
        besuClient
            .privGetTransactionCount(adder.getAddress().toHexString(), privacyGroupId)
            .send()
            .getTransactionCount();

    final Bytes payload =
        encodeAddToGroupFunctionCall(
            Bytes.fromBase64String(adder.getEnclaveKey()),
            addresses.stream().map(Bytes::fromBase64String).collect(Collectors.toList()));

    final RawPrivateTransaction privateTransaction =
        RawPrivateTransaction.createTransaction(
            nonce,
            BigInteger.valueOf(1000),
            BigInteger.valueOf(3000000),
            Address.PRIVACY_PROXY.toHexString(),
            payload.toHexString(),
            Base64String.wrap(adder.getEnclaveKey()),
            privacyGroupId,
            org.web3j.utils.Restriction.RESTRICTED);

    return besuClient
        .eeaSendRawTransaction(
            Numeric.toHexString(
                PrivateTransactionEncoder.signMessage(
                    privateTransaction, Credentials.create(adder.getTransactionSigningKey()))))
        .send()
        .getTransactionHash();
  }

  public String privxRemoveFromPrivacyGroup(
      final Base64String privacyGroupId, final PrivacyNode remover, final String toRemove)
      throws IOException, TransactionException {

    final BigInteger nonce =
        besuClient
            .privGetTransactionCount(remover.getAddress().toHexString(), privacyGroupId)
            .send()
            .getTransactionCount();

    final Bytes payload =
        encodeRemoveFromGroupFunctionCall(
            Bytes.fromBase64String(remover.getEnclaveKey()), Bytes.fromBase64String(toRemove));

    final RawPrivateTransaction privateTransaction =
        RawPrivateTransaction.createTransaction(
            nonce,
            BigInteger.valueOf(1000),
            BigInteger.valueOf(3000000),
            Address.PRIVACY_PROXY.toHexString(),
            payload.toHexString(),
            Base64String.wrap(remover.getEnclaveKey()),
            privacyGroupId,
            org.web3j.utils.Restriction.RESTRICTED);

    return besuClient
        .eeaSendRawTransaction(
            Numeric.toHexString(
                PrivateTransactionEncoder.signMessage(
                    privateTransaction, Credentials.create(remover.getTransactionSigningKey()))))
        .send()
        .getTransactionHash();
  }

  private Bytes encodeRemoveFromGroupFunctionCall(final Bytes remover, final Bytes toRemove) {
    return Bytes.concatenate(
        OnChainGroupManagement.REMOVE_PARTICIPANT_METHOD_SIGNATURE, remover, toRemove);
  }

  public String privxLockPrivacyGroup(final PrivacyNode locker, final Base64String privacyGroupId)
      throws IOException, TransactionException {
    final BigInteger nonce =
        besuClient
            .privGetTransactionCount(locker.getAddress().toHexString(), privacyGroupId)
            .send()
            .getTransactionCount();

    final RawPrivateTransaction privateTransaction =
        RawPrivateTransaction.createTransaction(
            nonce,
            BigInteger.valueOf(1000),
            BigInteger.valueOf(3000000),
            Address.PRIVACY_PROXY.toHexString(),
            OnChainGroupManagement.LOCK_GROUP_METHOD_SIGNATURE.toHexString(),
            Base64String.wrap(locker.getEnclaveKey()),
            privacyGroupId,
            org.web3j.utils.Restriction.RESTRICTED);

    final String transactionHash =
        besuClient
            .eeaSendRawTransaction(
                Numeric.toHexString(
                    PrivateTransactionEncoder.signMessage(
                        privateTransaction, Credentials.create(locker.getTransactionSigningKey()))))
            .send()
            .getTransactionHash();

    final PrivateTransactionReceipt privateTransactionReceipt =
        new PollingPrivateTransactionReceiptProcessor(besuClient, 3000, 10)
            .waitForTransactionReceipt(transactionHash);

    assertThat(privateTransactionReceipt.getStatus()).isEqualTo("0x1");

    return privateTransactionReceipt.getcommitmentHash();
  }

  public PrivxCreatePrivacyGroupResponse privxCreatePrivacyGroup(
      final PrivacyNode creator, final List<String> addresses) throws IOException {

    final byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    final Bytes privacyGroupId = Bytes.wrap(bytes);

    final BigInteger nonce =
        besuClient
            .privGetTransactionCount(
                creator.getAddress().toHexString(),
                Base64String.wrap(privacyGroupId.toArrayUnsafe()))
            .send()
            .getTransactionCount();

    final Bytes payload =
        encodeAddToGroupFunctionCall(
            Bytes.fromBase64String(creator.getEnclaveKey()),
            addresses.stream().map(Bytes::fromBase64String).collect(Collectors.toList()));

    final RawPrivateTransaction privateTransaction =
        RawPrivateTransaction.createTransaction(
            nonce,
            BigInteger.valueOf(1000),
            BigInteger.valueOf(3000000),
            Address.PRIVACY_PROXY.toHexString(),
            payload.toHexString(),
            Base64String.wrap(creator.getEnclaveKey()),
            Base64String.wrap(privacyGroupId.toArrayUnsafe()),
            org.web3j.utils.Restriction.RESTRICTED);

    final String transactionHash =
        besuClient
            .eeaSendRawTransaction(
                Numeric.toHexString(
                    PrivateTransactionEncoder.signMessage(
                        privateTransaction,
                        Credentials.create(creator.getTransactionSigningKey()))))
            .send()
            .getTransactionHash();
    return new PrivxCreatePrivacyGroupResponse(privacyGroupId.toBase64String(), transactionHash);
  }

  public Request<?, PrivxFindPrivacyGroupResponse> privxFindOnChainPrivacyGroup(
      final List<Base64String> nodes) {
    return new Request<>(
        "privx_findOnChainPrivacyGroup",
        singletonList(nodes),
        web3jService,
        PrivxFindPrivacyGroupResponse.class);
  }

  public Request<?, GetPrivacyPrecompileAddressResponse> privGetPrivacyPrecompileAddress() {
    return new Request<>(
        "priv_getPrivacyPrecompileAddress",
        Collections.emptyList(),
        web3jService,
        GetPrivacyPrecompileAddressResponse.class);
  }

  public Request<?, GetPrivateTransactionResponse> privGetPrivateTransaction(
      final Hash transactionHash) {
    return new Request<>(
        "priv_getPrivateTransaction",
        singletonList(transactionHash.toHexString()),
        web3jService,
        GetPrivateTransactionResponse.class);
  }

  public Request<?, CreatePrivacyGroupResponse> privCreatePrivacyGroup(
      final CreatePrivacyGroupParameter params) {
    return new Request<>(
        "priv_createPrivacyGroup",
        singletonList(params),
        web3jService,
        CreatePrivacyGroupResponse.class);
  }

  public Request<?, DeletePrivacyGroupResponse> privDeletePrivacyGroup(final String groupId) {
    return new Request<>(
        "priv_deletePrivacyGroup",
        singletonList(groupId),
        web3jService,
        DeletePrivacyGroupResponse.class);
  }

  public Request<?, FindPrivacyGroupResponse> privFindPrivacyGroup(final String[] groupMembers) {
    return new Request<>(
        "priv_findPrivacyGroup",
        singletonList(groupMembers),
        web3jService,
        FindPrivacyGroupResponse.class);
  }

  public Request<?, SendRawTransactionResponse> eeaSendRawTransaction(final String transaction) {
    return new Request<>(
        "eea_sendRawTransaction",
        singletonList(transaction),
        web3jService,
        SendRawTransactionResponse.class);
  }

  public Request<?, GetTransactionReceiptResponse> privGetTransactionReceipt(
      final Hash transactionHash) {
    return new Request<>(
        "priv_getTransactionReceipt",
        singletonList(transactionHash.toHexString()),
        web3jService,
        GetTransactionReceiptResponse.class);
  }

  public Request<?, GetTransactionCountResponse> privGetTransactionCount(final Object[] params) {
    return new Request<>(
        "priv_getTransactionCount",
        List.of(params),
        web3jService,
        GetTransactionCountResponse.class);
  }

  public Request<?, GetTransactionCountResponse> privGetEeaTransactionCount(final Object[] params) {
    return new Request<>(
        "priv_getEeaTransactionCount",
        List.of(params),
        web3jService,
        GetTransactionCountResponse.class);
  }

  public Request<?, GetCodeResponse> privGetCode(
      final String privacyGroupId, final String contractAddress, final String blockParameter) {
    return new Request<>(
        "priv_getCode",
        List.of(privacyGroupId, contractAddress, blockParameter),
        web3jService,
        GetCodeResponse.class);
  }

  public Request<?, EthCall> privCall(
      final String privacyGroupId,
      final Contract contract,
      final String encoded,
      final String blockNumberLatestPending) {

    final org.web3j.protocol.core.methods.request.Transaction transaction =
        org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
            null, contract.getContractAddress(), encoded);

    return new Request<>(
        "priv_call",
        Arrays.asList(privacyGroupId, transaction, blockNumberLatestPending),
        web3jService,
        EthCall.class);
  }

  public static class PrivxFindPrivacyGroupResponse extends Response<List<OnChainPrivacyGroup>> {

    public List<OnChainPrivacyGroup> getGroups() {
      return getResult();
    }
  }

  public static class OnChainPrivacyGroup {

    private final Base64String privacyGroupId;
    private final List<Base64String> members;
    private final String name;
    private final String description;

    public enum Type {
      ONCHAIN
    }

    @JsonCreator
    public OnChainPrivacyGroup(
        @JsonProperty(value = "privacyGroupId") final String privacyGroupId,
        @JsonProperty(value = "type") final Type type,
        @JsonProperty(value = "name") final String name,
        @JsonProperty(value = "description") final String description,
        @JsonProperty(value = "members") final List<Base64String> members) {
      this(privacyGroupId, members);
    }

    public OnChainPrivacyGroup(final String privacyGroupId, final List<Base64String> members) {
      this.privacyGroupId = Base64String.wrap(privacyGroupId);
      this.name = "";
      this.description = "";
      this.members = members;
    }

    public Base64String getPrivacyGroupId() {
      return privacyGroupId;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public Type getType() {
      return Type.ONCHAIN;
    }

    public List<Base64String> getMembers() {
      return members;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final OnChainPrivacyGroup that = (OnChainPrivacyGroup) o;
      return getPrivacyGroupId().equals(that.getPrivacyGroupId())
          && getName().equals(that.getName())
          && getDescription().equals(that.getDescription())
          && getType() == that.getType()
          && getMembers().equals(that.getMembers());
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          getPrivacyGroupId(), getName(), getDescription(), getType(), getMembers());
    }
  }

  public static class PrivxCreatePrivacyGroupResponse {

    final String privacyGroupId;
    final String transactionHash;

    @JsonCreator
    public PrivxCreatePrivacyGroupResponse(
        @JsonProperty("privacyGroupId") final String privacyGroupId,
        @JsonProperty("transactionHash") final String transactionHash) {
      this.privacyGroupId = privacyGroupId;
      this.transactionHash = transactionHash;
    }

    public String getPrivacyGroupId() {
      return privacyGroupId;
    }

    public String getTransactionHash() {
      return transactionHash;
    }
  }

  private Bytes encodeAddToGroupFunctionCall(
      final Bytes privateFrom, final List<Bytes> participants) {
    return Bytes.concatenate(
        OnChainGroupManagement.ADD_TO_GROUP_METHOD_SIGNATURE,
        privateFrom,
        encodeList(participants));
  }

  private Bytes encodeList(final List<Bytes> participants) {
    final Bytes dynamicParameterOffset = encodeLong(64);
    final Bytes length = encodeLong(participants.size());
    return Bytes.concatenate(
        dynamicParameterOffset,
        length,
        Bytes.fromHexString(
            participants.stream()
                .map(Bytes::toUnprefixedHexString)
                .collect(Collectors.joining(""))));
  }

  // long to uint256, 8 bytes big endian, so left padded by 24 bytes
  private static Bytes encodeLong(final long l) {
    checkArgument(l >= 0, "Unsigned value must be positive");
    final byte[] longBytes = new byte[8];
    for (int i = 0; i < 8; i++) {
      longBytes[i] = (byte) ((l >> ((7 - i) * 8)) & 0xFF);
    }
    return Bytes.concatenate(Bytes.wrap(new byte[24]), Bytes.wrap(longBytes));
  }
}
