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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.trie.verkle.SuffixStateDiff;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes32;

public class SuffixStateDiffParameter {

  private final byte suffix;
  private final Bytes32 currentValue;
  private final Bytes32 newValue;

  @JsonCreator
  public SuffixStateDiffParameter(
      @JsonProperty("suffix") final byte suffix,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("currentValue")
          final Bytes32 currentValue,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("newValue")
          final Bytes32 newValue) {
    this.suffix = suffix;
    this.currentValue = currentValue;
    this.newValue = newValue;
  }

  public static List<SuffixStateDiffParameter> fromSuffixStateDiff(
      final List<SuffixStateDiff> suffixStateDiffList) {
    final List<SuffixStateDiffParameter> suffixStateDiffParameterList =
        new ArrayList<SuffixStateDiffParameter>();
    for (SuffixStateDiff suffixStateDiff : suffixStateDiffList) {
      suffixStateDiffParameterList.add(
          new SuffixStateDiffParameter(
              suffixStateDiff.suffix(),
              suffixStateDiff.currentValue(),
              suffixStateDiff.newValue()));
    }
    return suffixStateDiffParameterList;
  }

  public static List<SuffixStateDiff> toSuffixStateDiff(
      final List<SuffixStateDiffParameter> suffixStateDiffParameterList) {
    final List<SuffixStateDiff> suffixStateDiffsList = new ArrayList<SuffixStateDiff>();
    for (SuffixStateDiffParameter suffixStateDiffParameter : suffixStateDiffParameterList) {
      suffixStateDiffsList.add(
          new SuffixStateDiff(
              suffixStateDiffParameter.getSuffix(),
              suffixStateDiffParameter.getCurrentValue(),
              suffixStateDiffParameter.getNewValue()));
    }
    return suffixStateDiffsList;
  }

  @Override
  public String toString() {
    return "SuffixStateDiff{" + "suffix=" + suffix + ", currentValue=" + currentValue;
  }

  @JsonGetter
  public byte getSuffix() {
    return suffix;
  }

  @JsonGetter
  public Bytes32 getCurrentValue() {
    return currentValue;
  }

  @JsonGetter
  public Bytes32 getNewValue() {
    return newValue;
  }
}
