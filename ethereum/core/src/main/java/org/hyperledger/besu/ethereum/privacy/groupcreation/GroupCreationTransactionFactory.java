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
package org.hyperledger.besu.ethereum.privacy.groupcreation;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public abstract class GroupCreationTransactionFactory {

  private static final Bytes DEFAULT_PRIVACY_MANAGEMENT_BYTECODE =
      Bytes.fromHexString("0xf744b089");

  public abstract PrivateTransaction create(
      final Bytes privacyGroupId, final Bytes privateFrom, final List<Bytes> participants);

  protected PrivateTransaction create(
      final Bytes privateFrom,
      final Bytes privacyGroupId,
      final List<Bytes> participants,
      final long nonce,
      final KeyPair signingKey) {
    final Bytes payload = encodeParameters(privateFrom, participants);
    return PrivateTransaction.builder()
        .nonce(nonce)
        .to(Address.PRIVACY_PROXY)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .value(Wei.ZERO)
        .payload(payload)
        .privateFrom(privateFrom)
        .privacyGroupId(privacyGroupId)
        .restriction(Restriction.RESTRICTED)
        .signAndBuild(signingKey);
  }

  private Bytes encodeParameters(final Bytes privateFrom, final List<Bytes> participants) {
    return Bytes.concatenate(
        DEFAULT_PRIVACY_MANAGEMENT_BYTECODE, privateFrom, encodeList(participants));
  }

  private Bytes encodeList(final List<Bytes> participants) {
    final Bytes dynamicParameterOffset = encodeLong(64);
    final Bytes length = encodeLong(participants.size());
    //    final Bytes padding = Bytes.wrap(new byte[(32 - (participants.size() % 32))])
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
