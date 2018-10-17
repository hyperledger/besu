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

import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A Transaction test case specification. */
@JsonIgnoreProperties({"_info"})
public class TransactionTestCaseSpec {

  public static class Expectation {

    private final Hash hash;

    private final Address sender;

    private final boolean succeeds;

    Expectation(
        @JsonProperty("hash") final String hash, @JsonProperty("sender") final String sender) {
      this.succeeds = hash != null && sender != null;
      if (succeeds) {
        this.hash = Hash.fromHexString(hash);
        this.sender = Address.fromHexString(sender);
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
  }

  private final HashMap<String, Expectation> expectations;

  private final BytesValue rlp;

  @JsonCreator
  public TransactionTestCaseSpec(
      @JsonProperty("Frontier") final Expectation frontierExpectation,
      @JsonProperty("Homestead") final Expectation homesteadExpectation,
      @JsonProperty("EIP150") final Expectation EIP150Expectation,
      @JsonProperty("EIP158") final Expectation EIP158Expectation,
      @JsonProperty("Byzantium") final Expectation byzantiumExpectation,
      @JsonProperty("Constantinople") final Expectation constantinopleExpectation,
      @JsonProperty("rlp") final String rlp) {
    expectations = new HashMap<>();
    expectations.put("Frontier", frontierExpectation);
    expectations.put("Homestead", homesteadExpectation);
    expectations.put("EIP150", EIP150Expectation);
    expectations.put("EIP158", EIP158Expectation);
    expectations.put("Byzantium", byzantiumExpectation);
    expectations.put("Constantinople", constantinopleExpectation);

    BytesValue parsedRlp = null;
    try {
      parsedRlp = BytesValue.fromHexString(rlp);
    } catch (final IllegalArgumentException e) {
      // Some test cases include rlp "hex strings" with invalid characters
      // In this case, just set rlp to null
    }
    this.rlp = parsedRlp;
  }

  public BytesValue getRlp() {
    return rlp;
  }

  public Expectation expectation(final String milestone) {
    final Expectation expectation = expectations.get(milestone);

    if (expectation == null) {
      throw new IllegalStateException("Expectation for milestone %s not found" + milestone);
    }

    return expectation;
  }
}
