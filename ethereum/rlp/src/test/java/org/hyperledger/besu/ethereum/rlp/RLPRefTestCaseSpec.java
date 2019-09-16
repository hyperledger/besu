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
package org.hyperledger.besu.ethereum.rlp;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

/**
 * A RLP reference test case specification.
 *
 * <p>Note: this class will be auto-generated with the JSON test specification.
 */
public class RLPRefTestCaseSpec {

  /** Prefix for integer-encoded string. */
  private static final String BIG_INT_PREFIX = "#";

  /** The test input. */
  private final Object in;

  /** The expected output. */
  private final BytesValue out;

  @SuppressWarnings("unchecked")
  private static Object parseIn(final Object in) {
    if (in instanceof String && ((String) in).startsWith(BIG_INT_PREFIX)) {
      return BytesValue.wrap(new BigInteger(((String) in).substring(1)).toByteArray());
    } else if (in instanceof String) {
      return BytesValue.wrap(((String) in).getBytes(UTF_8));
    } else if (in instanceof Integer) {
      return BytesValues.toMinimalBytes((Integer) in);
    } else if (in instanceof List) {
      return Lists.transform((List<Object>) in, RLPRefTestCaseSpec::parseIn);
    } else if (in instanceof Object[]) {
      return Arrays.stream((Object[]) in)
          .map(RLPRefTestCaseSpec::parseIn)
          .collect(Collectors.toList());
    } else {
      throw new IllegalArgumentException();
    }
  }

  /**
   * Public constructor.
   *
   * @param in The test input.
   * @param out The expected output.
   */
  @JsonCreator
  public RLPRefTestCaseSpec(
      @JsonProperty("in") final Object in, @JsonProperty("out") final String out) {
    // Check if the input is an integer-encoded string.
    this.in = parseIn(in);
    this.out = BytesValue.fromHexString(out);
  }

  /**
   * Returns the test input.
   *
   * @return The test input.
   */
  public Object getIn() {
    return in;
  }

  /**
   * Returns the expected output.
   *
   * @return The expected output.
   */
  public BytesValue getOut() {
    return out;
  }
}
