/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * Result returned by the 4byteTracer.
 *
 * <p>The 4byteTracer collects the function selectors of every function executed in the lifetime of
 * a transaction, along with the size of the supplied parameter data (excluding the 4-byte selector).
 * The result is a map where the keys are SELECTOR-PARAMETERDATASIZE and the values are number of 
 * occurrences of this key.
 *
 * The size represents len(input)-4, which is the size of the
 * parameter data excluding the function selector.
 *
 * <p>For example:
 * <pre>
 * {
 *   "0x27dc297e-128": 1,
 *   "0x38cc4831-0": 2,
 *   "0x524f3889-96": 1,
 *   "0xadf59f99-288": 1,
 *   "0xc281d19e-0": 1
 * }
 * </pre>
 */
@JsonPropertyOrder
public class FourByteTracerResult implements JsonRpcResult {

  private final Map<String, Integer> result;

  /**
   * Creates a new FourByteTracerResult with the given result map.
   *
   * @param result a map of function selector-calldata size to occurrence count
   */
  public FourByteTracerResult(final Map<String, Integer> result) {
    this.result = result;
  }

  /**
   * Returns the result map containing function selectors and their occurrence counts.
   *
   * @return a map where keys are "selector-calldatasize" and values are occurrence counts
   */
  @JsonGetter
  public Map<String, Integer> getResult() {
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final FourByteTracerResult that = (FourByteTracerResult) o;
    return result != null ? result.equals(that.result) : that.result == null;
  }

  @Override
  public int hashCode() {
    return result != null ? result.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "FourByteTracerResult{" + "result=" + result + '}';
  }
}

