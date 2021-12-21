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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

public class CliqueConfigOptions {

  public static final CliqueConfigOptions DEFAULT =
      new CliqueConfigOptions(JsonUtil.createEmptyObjectNode());

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 15;

  private final ObjectNode cliqueConfigRoot;

  CliqueConfigOptions(final ObjectNode cliqueConfigRoot) {
    this.cliqueConfigRoot = cliqueConfigRoot;
  }

  public long getEpochLength() {
    return JsonUtil.getLong(cliqueConfigRoot, "epochlength", DEFAULT_EPOCH_LENGTH);
  }

  public int getBlockPeriodSeconds() {
    return JsonUtil.getPositiveInt(
        cliqueConfigRoot, "blockperiodseconds", DEFAULT_BLOCK_PERIOD_SECONDS);
  }

  Map<String, Object> asMap() {
    return ImmutableMap.of(
        "epochLength", getEpochLength(), "blockPeriodSeconds", getBlockPeriodSeconds());
  }
}
