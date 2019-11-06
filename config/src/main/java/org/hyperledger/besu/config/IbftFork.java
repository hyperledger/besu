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
package org.hyperledger.besu.config;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

public class IbftFork {

  private final long forkBlock;
  private Integer blockPeriodSeconds;
  private Integer requestTimeoutSeconds;
  private List<String> validators;

  @JsonCreator
  public IbftFork(@JsonProperty("block") final long forkBlock) {
    this.forkBlock = forkBlock;
  }

  public long getForkBlock() {
    return forkBlock;
  }

  public Optional<Integer> getBlockPeriodSeconds() {
    return Optional.ofNullable(blockPeriodSeconds);
  }

  public Optional<Integer> getRequestTimeoutSeconds() {
    return Optional.ofNullable(requestTimeoutSeconds);
  }

  public Optional<List<String>> getValidators() {
    return Optional.ofNullable(validators);
  }

  @JsonSetter("blockperiodseconds")
  public void blockPeriodSeconds(final int blockPeriodSeconds) {
    this.blockPeriodSeconds = blockPeriodSeconds;
  }

  @JsonSetter("requesttimeoutseconds")
  public void requestTimeoutSeconds(final int requestTimeoutSeconds) {
    this.requestTimeoutSeconds = requestTimeoutSeconds;
  }

  @JsonSetter("validators")
  public void validators(final List<String> validators) {
    this.validators = validators;
  }
}
