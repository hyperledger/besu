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
 *
 */
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

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
 *       this class in {@link #get(GeneralStateTestCaseSpec.Indexes)}.
 *   <li>the signature of the transaction is not provided in the json directly. Instead, the private
 *       key of the sender is provided, and the transaction must thus be signed (also in {@link
 *       #get(GeneralStateTestCaseSpec.Indexes)}) through {@link
 *       Transaction.Builder#signAndBuild(KeyPair)}.
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateTestVersionedTransaction {

  private final long nonce;
  private final Wei gasPrice;
  @Nullable private final Address to;

  private final KeyPair keys;

  private final List<Gas> gasLimits;
  private final List<Wei> values;
  private final List<Bytes> payloads;

  /**
   * Constructor for populating a mock transaction with json data.
   *
   * @param nonce Nonce of the mock transaction.
   * @param gasPrice Gas price of the mock transaction.
   * @param gasLimit Gas Limit of the mock transaction.
   * @param to Recipient account of the mock transaction.
   * @param value Amount of ether transferred in the mock transaction.
   * @param secretKey Secret Key of the mock transaction.
   * @param data Call data of the mock transaction.
   */
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

    SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    this.keys =
        signatureAlgorithm.createKeyPair(
            signatureAlgorithm.createPrivateKey(Bytes32.fromHexString(secretKey)));

    this.gasLimits = parseArray(gasLimit, Gas::fromHexString);
    this.values = parseArray(value, Wei::fromHexString);
    this.payloads = parseArray(data, Bytes::fromHexString);
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
        .guessType()
        .signAndBuild(keys);
  }
}
