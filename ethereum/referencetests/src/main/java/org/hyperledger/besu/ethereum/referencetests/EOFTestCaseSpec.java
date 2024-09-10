/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.referencetests;

import java.util.NavigableMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EOFTestCaseSpec {

  public record TestVector(
      @JsonProperty("code") String code,
      @JsonProperty("results") NavigableMap<String, TestResult> results,
      @JsonProperty("containerKind") String containerKind) {}

  public record TestResult(
      @JsonProperty("exception") String exception, @JsonProperty("result") boolean result) {
    public static TestResult TEST_RESULT_PASSED = new TestResult(null, true);

    public static TestResult failed(final String exception) {
      return new TestResult(exception, false);
    }

    public static TestResult passed() {
      return TEST_RESULT_PASSED;
    }
  }

  NavigableMap<String, TestVector> vector;

  @JsonCreator
  public EOFTestCaseSpec(@JsonProperty("vectors") final NavigableMap<String, TestVector> vector) {
    this.vector = vector;
  }

  public NavigableMap<String, TestVector> getVector() {
    return vector;
  }
}
