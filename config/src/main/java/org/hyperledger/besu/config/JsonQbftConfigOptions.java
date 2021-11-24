/*
 * Copyright Hyperledger Besu Contributors.
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

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

public class JsonQbftConfigOptions extends JsonBftConfigOptions implements QbftConfigOptions {
  public static final JsonQbftConfigOptions DEFAULT =
      new JsonQbftConfigOptions(JsonUtil.createEmptyObjectNode());
  public static final String VALIDATOR_CONTRACT_ADDRESS = "validatorcontractaddress";
  public static final String START_BLOCK = "startblock";

  public JsonQbftConfigOptions(final ObjectNode bftConfigRoot) {
    super(bftConfigRoot);
  }

  @Override
  public Optional<String> getValidatorContractAddress() {
    return JsonUtil.getString(bftConfigRoot, VALIDATOR_CONTRACT_ADDRESS).map(String::toLowerCase);
  }

  @Override
  public OptionalLong getStartBlock() {
    return JsonUtil.getLong(bftConfigRoot, START_BLOCK);
  }

  @Override
  public Map<String, Object> asMap() {
    final Map<String, Object> map = super.asMap();
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.putAll(map);

    getValidatorContractAddress()
        .ifPresent((address) -> builder.put(VALIDATOR_CONTRACT_ADDRESS, address));
    getStartBlock().ifPresent((startBlock) -> builder.put(START_BLOCK, getStartBlock()));

    return builder.build();
  }
}
