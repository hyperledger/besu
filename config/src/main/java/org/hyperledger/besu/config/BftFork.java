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

import org.hyperledger.besu.datatypes.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;

public class BftFork {

  public static final String FORK_BLOCK_KEY = "block";
  public static final String VALIDATORS_KEY = "validators";
  public static final String BLOCK_PERIOD_SECONDS_KEY = "blockperiodseconds";
  public static final String BLOCK_REWARD_KEY = "blockreward";
  public static final String MINING_BENEFICIARY_KEY = "miningbeneficiary";

  protected final ObjectNode forkConfigRoot;

  @JsonCreator
  public BftFork(final ObjectNode forkConfigRoot) {
    this.forkConfigRoot = forkConfigRoot;
  }

  public long getForkBlock() {
    return JsonUtil.getLong(forkConfigRoot, FORK_BLOCK_KEY)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Fork block not specified for Bft fork in custom forks"));
  }

  public OptionalInt getBlockPeriodSeconds() {
    return JsonUtil.getPositiveInt(forkConfigRoot, BLOCK_PERIOD_SECONDS_KEY);
  }

  public Optional<BigInteger> getBlockRewardWei() {
    final Optional<String> configFileContent = JsonUtil.getString(forkConfigRoot, BLOCK_REWARD_KEY);

    if (configFileContent.isEmpty()) {
      return Optional.empty();
    }
    final String weiStr = configFileContent.get();
    if (weiStr.toLowerCase().startsWith("0x")) {
      return Optional.of(new BigInteger(1, Bytes.fromHexStringLenient(weiStr).toArrayUnsafe()));
    }
    return Optional.of(new BigInteger(weiStr));
  }

  public Optional<Address> getMiningBeneficiary() {
    try {
      return JsonUtil.getString(forkConfigRoot, MINING_BENEFICIARY_KEY)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(Address::fromHexStringStrict);
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Mining beneficiary in transition config is not a valid ethereum address", e);
    }
  }

  public boolean isMiningBeneficiaryConfigured() {
    return JsonUtil.hasKey(forkConfigRoot, MINING_BENEFICIARY_KEY);
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
                    "Bft Validator fork does not contain a string " + value);
              }

              validators.add(value.asText());
            });
    return Optional.of(validators);
  }
}
