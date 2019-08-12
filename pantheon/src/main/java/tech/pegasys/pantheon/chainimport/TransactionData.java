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
package tech.pegasys.pantheon.chainimport;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.PrivateKey;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionData {

  private final long gasLimit;
  private final Wei gasPrice;
  private final BytesValue data;
  private final Wei value;
  private final Optional<Address> to;
  private final PrivateKey privateKey;

  @JsonCreator
  public TransactionData(
      @JsonProperty("gasLimit") final String gasLimit,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("data") final Optional<String> data,
      @JsonProperty("value") final Optional<String> value,
      @JsonProperty("to") final Optional<String> to,
      @JsonProperty("fromPrivateKey") final String fromPrivateKey) {
    this.gasLimit = UInt256.fromHexString(gasLimit).toLong();
    this.gasPrice = Wei.fromHexString(gasPrice);
    this.data = data.map(BytesValue::fromHexString).orElse(BytesValue.EMPTY);
    this.value = value.map(Wei::fromHexString).orElse(Wei.ZERO);
    this.to = to.map(Address::fromHexString);
    this.privateKey = PrivateKey.create(Bytes32.fromHexString(fromPrivateKey));
  }

  public Transaction getSignedTransaction(final NonceProvider nonceProvider) {
    KeyPair keyPair = KeyPair.create(privateKey);

    final Address fromAddress = Address.extract(keyPair.getPublicKey());
    final long nonce = nonceProvider.get(fromAddress);
    return Transaction.builder()
        .gasLimit(gasLimit)
        .gasPrice(gasPrice)
        .nonce(nonce)
        .payload(data)
        .value(value)
        .to(to.orElse(null))
        .signAndBuild(keyPair);
  }

  @FunctionalInterface
  public interface NonceProvider {
    long get(final Address address);
  }
}
