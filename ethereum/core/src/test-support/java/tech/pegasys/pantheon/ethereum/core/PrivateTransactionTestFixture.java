/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransaction;
import tech.pegasys.pantheon.ethereum.privacy.Restriction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;

public class PrivateTransactionTestFixture {

  private long nonce = 0;

  private Wei gasPrice = Wei.of(5);

  private long gasLimit = 5000;

  private Optional<Address> to = Optional.empty();
  private Address sender = Address.fromHexString(String.format("%020x", 1));

  private Wei value = Wei.of(4);

  private BytesValue payload = BytesValue.EMPTY;

  private Optional<BigInteger> chainId = Optional.of(BigInteger.valueOf(2018));

  private BytesValue privateFrom =
      BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

  private Optional<List<BytesValue>> privateFor =
      Optional.of(
          Lists.newArrayList(
              BytesValues.fromBase64("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")));

  private Optional<BytesValue> privacyGroupId = Optional.empty();

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
      this.privacyGroupId(privacyGroupId.get());
    } else {
      privateFor.ifPresent(builder::privateFor);
    }

    if (privacyGroupId.isEmpty() && privateFor.isEmpty()) {
      throw new IllegalArgumentException(
          "Private transaction needs a privacyGroupId or privateFor field");
    }

    return builder.signAndBuild(keys);
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

  public PrivateTransactionTestFixture payload(final BytesValue payload) {
    this.payload = payload;
    return this;
  }

  public PrivateTransactionTestFixture chainId(final Optional<BigInteger> chainId) {
    this.chainId = chainId;
    return this;
  }

  public PrivateTransactionTestFixture privateFrom(final BytesValue privateFrom) {
    this.privateFrom = privateFrom;
    return this;
  }

  public PrivateTransactionTestFixture privateFor(final List<BytesValue> privateFor) {
    this.privateFor = Optional.of(privateFor);
    return this;
  }

  public PrivateTransactionTestFixture privacyGroupId(final BytesValue privacyGroupId) {
    this.privacyGroupId = Optional.of(privacyGroupId);
    return this;
  }
}
