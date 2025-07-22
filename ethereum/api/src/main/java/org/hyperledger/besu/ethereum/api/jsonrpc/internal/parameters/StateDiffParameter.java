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

import org.hyperledger.besu.ethereum.core.witness.StateDiff;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

public class StateDiffParameter {

  final List<StemStateDiffParameter> steamStateDiff;

  @JsonCreator
  public StateDiffParameter(final List<StemStateDiffParameter> steamStateDiff) {
    this.steamStateDiff = steamStateDiff;
  }

  public List<StemStateDiffParameter> getSteamStateDiff() {
    return steamStateDiff;
  }

  public static StateDiff toStateDiff(final StateDiffParameter stateDiffParameter) {
    return new StateDiff(
        StemStateDiffParameter.toListOfStemStateDiff(stateDiffParameter.getSteamStateDiff()));
  }

  public static StateDiffParameter fromStateDiff(final StateDiff stateDiff) {
    return new StateDiffParameter(
        StemStateDiffParameter.fromListOfStemStateDiff(stateDiff.steamStateDiff()));
  }

  @Override
  public String toString() {
    return "StateDiffParameter{" + "steamStateDiff=" + steamStateDiff + '}';
  }
}
