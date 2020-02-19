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
import java.util.OptionalInt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

public class IbftFork {

  private static final String FORK_BLOCK_KEY = "block";
  private static final String VALIDATORS_KEY = "validators";
  private static final String BLOCK_PERIOD_SECONDS_KEY = "blockperiodseconds";

  private final ObjectNode forkConfigRoot;

  @JsonCreator
  public IbftFork(final ObjectNode forkConfigRoot) {
    this.forkConfigRoot = forkConfigRoot;
  }

  public long getForkBlock() {
    return JsonUtil.getLong(forkConfigRoot, FORK_BLOCK_KEY)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Fork block not specified for IBFT2 fork in custom forks"));
  }

  public OptionalInt getBlockPeriodSeconds() {
    return JsonUtil.getInt(forkConfigRoot, BLOCK_PERIOD_SECONDS_KEY);
  }

  public Optional<List<String>> getValidators() throws IllegalArgumentException {
    final Optional<ArrayNode> validatorNode = JsonUtil.getArrayNode(forkConfigRoot, VALIDATORS_KEY);

    if (validatorNode.isEmpty()) {
      return Optional.empty();
    }

    List<String> validators = Lists.newArrayList();
    validatorNode
        .get()
        .elements()
        .forEachRemaining(
            value -> {
              if (!value.isTextual()) {
                throw new IllegalArgumentException(
                    "Ibft Validator fork does not contain a string " + value.toString());
              }

              validators.add(value.asText());
            });
    return Optional.of(validators);
  }
}
