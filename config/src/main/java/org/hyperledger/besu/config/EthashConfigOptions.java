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

import java.util.Map;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

public class EthashConfigOptions {

  public static final EthashConfigOptions DEFAULT =
      new EthashConfigOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode ethashConfigRoot;

  EthashConfigOptions(final ObjectNode ethashConfigRoot) {
    this.ethashConfigRoot = ethashConfigRoot;
  }

  public OptionalLong getFixedDifficulty() {
    return JsonUtil.getLong(ethashConfigRoot, "fixeddifficulty");
  }

  Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    getFixedDifficulty().ifPresent(l -> builder.put("fixeddifficulty", l));
    return builder.build();
  }
}
