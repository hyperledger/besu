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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static java.util.stream.Collectors.toUnmodifiableList;

import org.hyperledger.besu.datatypes.Wei;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.immutables.value.Value;

@Value.Immutable
public interface FeeHistory {

  long getOldestBlock();

  List<Wei> getBaseFeePerGas();

  List<Double> getGasUsedRatio();

  Optional<List<List<Wei>>> getReward();

  @Value.Immutable
  @Value.Style(allParameters = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  interface FeeHistoryResult {
    @JsonProperty("oldestBlock")
    String getOldestBlock();

    @JsonProperty("baseFeePerGas")
    List<String> getBaseFeePerGas();

    @JsonProperty("gasUsedRatio")
    List<Double> getGasUsedRatio();

    @Nullable
    @JsonProperty("reward")
    List<List<String>> getReward();

    static FeeHistoryResult from(final FeeHistory feeHistory) {
      return ImmutableFeeHistoryResult.of(
          Quantity.create(feeHistory.getOldestBlock()),
          feeHistory.getBaseFeePerGas().stream()
              .map(Quantity::create)
              .collect(toUnmodifiableList()),
          feeHistory.getGasUsedRatio(),
          feeHistory
              .getReward()
              .map(
                  outerList ->
                      outerList.stream()
                          .map(
                              innerList ->
                                  innerList.stream()
                                      .map(Quantity::create)
                                      .collect(toUnmodifiableList()))
                          .collect(toUnmodifiableList()))
              .orElse(null));
    }
  }
}
