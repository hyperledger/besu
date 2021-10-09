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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.VersionedPrivateTransaction;
import org.hyperledger.besu.plugin.data.Restriction;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateTransactionTestFixture {

  private long nonce = 0;

  private Wei gasPrice = Wei.of(5);

  private long gasLimit = 5000;

  private Optional<Address> to = Optional.empty();

  private Address sender = Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73");

  private Wei value = Wei.of(0);

  private Bytes payload = Bytes.EMPTY;

  private Optional<BigInteger> chainId = Optional.of(BigInteger.valueOf(2018));

  private Bytes privateFrom =
      Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

  private Optional<List<Bytes>> privateFor =
      Optional.of(
          Lists.newArrayList(
              Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")));

  private Optional<Bytes> privacyGroupId = Optional.empty();

  private Restriction restriction = Restriction.RESTRICTED;

  public PrivateTransaction createTransaction(final KeyPair keys) {
    final PrivateTransaction.Builder builder = PrivateTransaction.builder();
    builder
        .gasLimit(gasLimit)
        .gasPrice(gasPrice)
        .nonce(nonce)
        .payload(payload)
        .value(value)
        .sender(sender)
        .privateFrom(privateFrom)
        .restriction(restriction);

    to.ifPresent(builder::to);
    chainId.ifPresent(builder::chainId);

    if (privacyGroupId.isPresent()) {
      privacyGroupId.ifPresent(builder::privacyGroupId);
    } else {
      privateFor.ifPresent(builder::privateFor);
    }

    if (privacyGroupId.isEmpty() && privateFor.isEmpty()) {
      throw new IllegalArgumentException(
          "Private transaction needs a privacyGroupId or privateFor field");
    }

    return builder.signAndBuild(keys);
  }

  public VersionedPrivateTransaction createVersionedPrivateTransaction(final KeyPair keyPair) {
    final PrivateTransaction transaction = createTransaction(keyPair);
    return new VersionedPrivateTransaction(transaction, Bytes32.ZERO);
  }

  public PrivateTransactionTestFixture nonce(final long nonce) {
    this.nonce = nonce;
    return this;
  }

  public PrivateTransactionTestFixture gasPrice(final Wei gasPrice) {
    this.gasPrice = gasPrice;
    return this;
  }

  public PrivateTransactionTestFixture gasLimit(final long gasLimit) {
    this.gasLimit = gasLimit;
    return this;
  }

  public PrivateTransactionTestFixture to(final Optional<Address> to) {
    this.to = to;
    return this;
  }

  public PrivateTransactionTestFixture sender(final Address sender) {
    this.sender = sender;
    return this;
  }

  public PrivateTransactionTestFixture value(final Wei value) {
    this.value = value;
    return this;
  }

  public PrivateTransactionTestFixture payload(final Bytes payload) {
    this.payload = payload;
    return this;
  }

  public PrivateTransactionTestFixture chainId(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
    return this;
  }

  public PrivateTransactionTestFixture privateFrom(final Bytes privateFrom) {
    this.privateFrom = privateFrom;
    return this;
  }

  public PrivateTransactionTestFixture privateFor(final List<Bytes> privateFor) {
    this.privateFor = Optional.of(privateFor);
    return this;
  }

  public PrivateTransactionTestFixture privacyGroupId(final Bytes privacyGroupId) {
    this.privacyGroupId = Optional.of(privacyGroupId);
    return this;
  }

  public PrivateTransactionTestFixture restriction(final Restriction restriction) {
    this.restriction = restriction;
    return this;
  }
}
