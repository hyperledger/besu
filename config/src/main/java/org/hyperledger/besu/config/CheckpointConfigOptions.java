/*
 * Copyright contributors to Hyperledger Besu
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

public class CheckpointConfigOptions {

  public static final CheckpointConfigOptions DEFAULT =
      new CheckpointConfigOptions(JsonUtil.createEmptyObjectNode());

  private final ObjectNode checkpointConfigRoot;

  CheckpointConfigOptions(final ObjectNode checkpointConfigRoot) {
    this.checkpointConfigRoot = checkpointConfigRoot;
  }

  public Optional<String> getTotalDifficulty() {
    return JsonUtil.getString(checkpointConfigRoot, "totaldifficulty");
  }

  public OptionalLong getNumber() {
    return JsonUtil.getLong(checkpointConfigRoot, "number");
  }

  public Optional<String> getHash() {
    return JsonUtil.getString(checkpointConfigRoot, "hash");
  }

  public boolean isValid() {
    return getTotalDifficulty().isPresent() && getNumber().isPresent() && getHash().isPresent();
  }

  Map<String, Object> asMap() {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    getTotalDifficulty().ifPresent(l -> builder.put("totaldifficulty", l));
    getNumber().ifPresent(l -> builder.put("number", l));
    getHash().ifPresent(l -> builder.put("hash", l));
    return builder.build();
  }

  @Override
  public String toString() {
    return "CheckpointConfigOptions{" + "checkpointConfigRoot=" + checkpointConfigRoot + '}';
  }
}
