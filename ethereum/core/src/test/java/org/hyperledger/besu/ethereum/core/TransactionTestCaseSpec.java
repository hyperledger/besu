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
import org.apache.tuweni.bytes.Bytes;

/** A Transaction test case specification. */
@JsonIgnoreProperties({"_info"})
public class TransactionTestCaseSpec {

  public static class Expectation {

    private final Hash hash;

    private final Address sender;

    private final boolean succeeds;

    Expectation(final String hash, final String sender) {
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
      return succeeds;
    }

    public Hash getHash() {
      return hash;
    }

    public Address getSender() {
      return sender;
    }
  }

  private final HashMap<String, Expectation> expectations;

  private final Bytes txBytes;

  @SuppressWarnings("unchecked")
  @JsonCreator
  public TransactionTestCaseSpec(final Map<String, Object> props) {
    expectations = new HashMap<>();
    for (final Map.Entry<String, Object> expectationJsonObject :
        ((Map<String, Object>) props.get("result")).entrySet()) {
      final Map<String, Object> expectation =
          (Map<String, Object>) expectationJsonObject.getValue();
      expectations.put(
          expectationJsonObject.getKey(),
          new Expectation((String) expectation.get("hash"), (String) expectation.get("sender")));
    }

    Bytes parsedTxBytes = null;
    try {
      parsedTxBytes = Bytes.fromHexString(props.get("txbytes").toString());
    } catch (final IllegalArgumentException e) {
      // Some test cases include txbytes "hex strings" with invalid characters
      // In this case, just set txbytes to null
    }
    this.txBytes = parsedTxBytes;
  }

  public Bytes getTxBytes() {
    return txBytes;
  }

  public Expectation expectation(final String milestone) {
    final Expectation expectation = expectations.get(milestone);

    if (expectation == null) {
      throw new IllegalStateException("Expectation for milestone " + milestone + " not found");
    }

    return expectation;
  }
}
