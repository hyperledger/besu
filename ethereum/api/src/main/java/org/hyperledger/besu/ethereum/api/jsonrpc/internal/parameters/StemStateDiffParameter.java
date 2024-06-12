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
import org.hyperledger.besu.ethereum.core.witness.StemStateDiff;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;

public class StemStateDiffParameter {
  final Bytes stem;
  final List<SuffixStateDiffParameter> suffixDiffs;

  @JsonCreator
  public StemStateDiffParameter(
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("stem") final Bytes stem,
      @JsonProperty("suffixDiffs") final List<SuffixStateDiffParameter> suffixDiffs) {
    this.stem = stem;
    this.suffixDiffs = suffixDiffs;
  }

  public static List<StemStateDiffParameter> fromListOfStemStateDiff(
      final List<StemStateDiff> stateDiff) {
    List<StemStateDiffParameter> stemStateDiffParameterList =
        new ArrayList<StemStateDiffParameter>();
    for (StemStateDiff stemStateDiff : stateDiff) {
      stemStateDiffParameterList.add(
          new StemStateDiffParameter(
              stemStateDiff.stem(),
              SuffixStateDiffParameter.fromSuffixStateDiff(stemStateDiff.suffixDiffs())));
    }
    return stemStateDiffParameterList;
  }

  public static List<StemStateDiff> toListOfStemStateDiff(
      final List<StemStateDiffParameter> stemStateDiffParameterList) {
    List<StemStateDiff> stemStateDiffs = new ArrayList<StemStateDiff>();
    for (StemStateDiffParameter stemStateDiffParameter : stemStateDiffParameterList) {
      stemStateDiffs.add(
          new StemStateDiff(
              stemStateDiffParameter.getStem(),
              SuffixStateDiffParameter.toSuffixStateDiff(stemStateDiffParameter.getSuffixDiffs())));
    }
    return stemStateDiffs;
  }

  @JsonGetter
  public Bytes getStem() {
    return stem;
  }

  @JsonGetter
  public List<SuffixStateDiffParameter> getSuffixDiffs() {
    return suffixDiffs;
  }
}
