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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

/** A Transaction test case specification. */
@JsonIgnoreProperties({"_info"})
public class TransactionTestCaseSpec {

  public static class Expectation {

    private final Hash hash;

    private final Address sender;

    private long intrinsicGas;

    private final boolean succeeds;

    Expectation(
        @JsonProperty("exception") final String exception,
        @JsonProperty("hash") final String hash,
        @JsonProperty("intrinsicGas") final String intrinsicGas,
        @JsonProperty("sender") final String sender) {
      this.succeeds = exception == null;
      if (succeeds) {
        this.hash = Hash.fromHexString(hash);
        this.sender = Address.fromHexString(sender);
        this.intrinsicGas = Long.decode(intrinsicGas);
      } else {
        this.hash = null;
        this.sender = null;
      }
    }

    public boolean isSucceeds() {
      return this.succeeds;
    }

    public Hash getHash() {
      return this.hash;
    }

    public Address getSender() {
      return this.sender;
    }

    public long getIntrinsicGas() {
      return intrinsicGas;
    }
  }

  private final HashMap<String, Expectation> expectations;

  private final Bytes rlp;

  @SuppressWarnings("unchecked")
  @JsonCreator
  public TransactionTestCaseSpec(final Map<String, Object> props) {
    expectations = new HashMap<>();
    var result = (Map<String, Object>) props.get("result");
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final Map<String, Object> expectation = (Map<String, Object>) entry.getValue();
      expectations.put(
          entry.getKey(),
          new Expectation(
              (String) expectation.get("exception"),
              (String) expectation.get("hash"),
              (String) expectation.get("intrinsicGas"),
              (String) expectation.get("sender")));
    }

    Bytes parsedRlp = null;
    try {
      parsedRlp = Bytes.fromHexString(props.get("txbytes").toString());
    } catch (final IllegalArgumentException e) {
      // Some test cases include rlp "hex strings" with invalid characters
      // In this case, just set rlp to null
    }
    this.rlp = parsedRlp;
  }

  public Bytes getRlp() {
    return rlp;
  }

  public Expectation expectation(final String milestone) {
    final Expectation expectation = expectations.get(milestone);

    if (expectation == null) {
      throw new IllegalStateException("Expectation for milestone " + milestone + " not found");
    }

    return expectation;
  }
}
