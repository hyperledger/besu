/*
 * Copyright 2020 Whiteblock Inc.
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
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

public class Keccak256PowConfigOptions {

  public static final Keccak256PowConfigOptions DEFAULT =
      new Keccak256PowConfigOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode keccak256PowConfigRoot;

  Keccak256PowConfigOptions(final ObjectNode keccak256PowConfigRoot) {
    this.keccak256PowConfigRoot = keccak256PowConfigRoot;
  }

  public OptionalLong getFixedDifficulty() {
    return JsonUtil.getLong(keccak256PowConfigRoot, "fixeddifficulty");
  }

  Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    getFixedDifficulty().ifPresent(l -> builder.put("fixeddifficulty", l));
    return builder.build();
  }
}
