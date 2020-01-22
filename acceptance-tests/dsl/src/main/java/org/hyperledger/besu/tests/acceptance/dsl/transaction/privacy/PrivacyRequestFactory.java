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

import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.besu.Besu;
import org.web3j.protocol.besu.response.privacy.PrivFindPrivacyGroup;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.eea.crypto.PrivateTransactionEncoder;
import org.web3j.protocol.eea.crypto.RawPrivateTransaction;
import org.web3j.utils.Base64String;
import org.web3j.utils.Numeric;

public class PrivacyRequestFactory {
  private static final Bytes DEFAULT_PRIVACY_ADD_METHOD_SIGNATURE =
      Bytes.fromHexString("0xf744b089");
  private final Besu besuClient;
  private final Web3jService web3jService;
  private final SecureRandom secureRandom;

  public PrivacyRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
    this.besuClient = Besu.build(web3jService);
    this.secureRandom = SecureRandomProvider.createSecureRandom();
  }

  public Besu getBesuClient() {
    return besuClient;
  }

  public PrivxCreatePrivacyGroup privxCreatePrivacyGroup(
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
        encodeParameters(
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
    return new PrivxCreatePrivacyGroup(privacyGroupId.toBase64String(), transactionHash);
  }

  public Request<?, PrivFindPrivacyGroup> privxFindPrivacyGroup(final List<Base64String> nodes) {
    return new Request<>(
        "privx_findPrivacyGroup", singletonList(nodes), web3jService, PrivFindPrivacyGroup.class);
  }

  public Request<?, PrivDistributeTransactionResponse> privDistributeTransaction(
      final String signedPrivateTransaction) {
    return new Request<>(
        "priv_distributeRawTransaction",
        singletonList(signedPrivateTransaction),
        web3jService,
        PrivDistributeTransactionResponse.class);
  }

  public static class PrivDistributeTransactionResponse extends Response<String> {

    public PrivDistributeTransactionResponse() {}

    public String getTransactionKey() {
      return getResult();
    }
  }

  public static class PrivxCreatePrivacyGroup {
    final String privacyGroupId;
    final String transactionHash;

    @JsonCreator
    public PrivxCreatePrivacyGroup(
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

  private Bytes encodeParameters(final Bytes privateFrom, final List<Bytes> participants) {
    return Bytes.concatenate(
        DEFAULT_PRIVACY_ADD_METHOD_SIGNATURE, privateFrom, encodeList(participants));
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
