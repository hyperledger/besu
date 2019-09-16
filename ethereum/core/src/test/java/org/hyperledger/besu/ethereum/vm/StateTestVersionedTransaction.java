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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.PrivateKey;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the "transaction" part of the JSON of a general state tests.
 *
 * <p>This contains information for a transaction, indirectly versioned by milestone. More
 * precisely, this there is 2 steps to transform this class (which again, represent what is in the
 * JSON) to an actual transaction to test:
 *
 * <ul>
 *   <li>in the state test json, gas, value and data for the transaction are arrays. This is how
 *       state tests deal with milestone versioning: for a given milestone, the actual value to use
 *       is defined by the indexes of the "post" section of the json. Those indexes are passed to
 *       this class in {@link #get(Indexes)}.
 *   <li>the signature of the transaction is not provided in the json directly. Instead, the private
 *       key of the sender is provided, and the transaction must thus be signed (also in {@link
 *       #get(Indexes)}) through {@link Transaction.Builder#signAndBuild(KeyPair)}.
 * </ul>
 */
public class StateTestVersionedTransaction {

  private final long nonce;
  private final Wei gasPrice;
  @Nullable private final Address to;

  private final KeyPair keys;

  private final List<Gas> gasLimits;
  private final List<Wei> values;
  private final List<BytesValue> payloads;

  /** Constructor for populating a mock account with json data. */
  @JsonCreator
  public StateTestVersionedTransaction(
      @JsonProperty("nonce") final String nonce,
      @JsonProperty("gasPrice") final String gasPrice,
      @JsonProperty("gasLimit") final String[] gasLimit,
      @JsonProperty("to") final String to,
      @JsonProperty("value") final String[] value,
      @JsonProperty("secretKey") final String secretKey,
      @JsonProperty("data") final String[] data) {

    this.nonce = Long.decode(nonce);
    this.gasPrice = Wei.fromHexString(gasPrice);
    this.to = to.isEmpty() ? null : Address.fromHexString(to);
    this.keys = KeyPair.create(PrivateKey.create(Bytes32.fromHexString(secretKey)));

    this.gasLimits = parseArray(gasLimit, Gas::fromHexString);
    this.values = parseArray(value, Wei::fromHexString);
    this.payloads = parseArray(data, BytesValue::fromHexString);
  }

  private static <T> List<T> parseArray(final String[] array, final Function<String, T> parseFct) {
    final List<T> res = new ArrayList<>(array.length);
    for (final String str : array) {
      res.add(parseFct.apply(str));
    }
    return res;
  }

  public Transaction get(final GeneralStateTestCaseSpec.Indexes indexes) {
    return Transaction.builder()
        .nonce(nonce)
        .gasPrice(gasPrice)
        .gasLimit(gasLimits.get(indexes.gas).asUInt256().toLong())
        .to(to)
        .value(values.get(indexes.value))
        .payload(payloads.get(indexes.data))
        .signAndBuild(keys);
  }
}
