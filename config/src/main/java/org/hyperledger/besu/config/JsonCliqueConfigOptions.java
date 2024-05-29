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
package org.hyperledger.besu.config;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

/** The Clique config options. */
public class JsonCliqueConfigOptions implements CliqueConfigOptions {

  private static final long DEFAULT_EPOCH_LENGTH = 30_000;
  private static final int DEFAULT_BLOCK_PERIOD_SECONDS = 15;
  private static final boolean DEFAULT_CREATE_EMPTY_BLOCKS = true;

  private final ObjectNode cliqueConfigRoot;

  /** The constant DEFAULT. */
  public static final JsonCliqueConfigOptions DEFAULT =
      new JsonCliqueConfigOptions(JsonUtil.createEmptyObjectNode());

  /**
   * Instantiates a new Clique config options.
   *
   * @param cliqueConfigRoot the clique config root
   */
  JsonCliqueConfigOptions(final ObjectNode cliqueConfigRoot) {
    this.cliqueConfigRoot = cliqueConfigRoot;
  }

  /**
   * The number of blocks in an epoch.
   *
   * @return the epoch length
   */
  @Override
  public long getEpochLength() {
    return JsonUtil.getLong(cliqueConfigRoot, "epochlength", DEFAULT_EPOCH_LENGTH);
  }

  /**
   * Gets block period seconds.
   *
   * @return the block period seconds
   */
  @Override
  public int getBlockPeriodSeconds() {
    return JsonUtil.getPositiveInt(
        cliqueConfigRoot, "blockperiodseconds", DEFAULT_BLOCK_PERIOD_SECONDS);
  }

  /**
   * Whether the creation of empty blocks is allowed.
   *
   * @return the create empty block status
   */
  @Override
  public boolean getCreateEmptyBlocks() {
    return JsonUtil.getBoolean(cliqueConfigRoot, "createemptyblocks", DEFAULT_CREATE_EMPTY_BLOCKS);
  }

  /**
   * As map.
   *
   * @return the map
   */
  @Override
  public Map<String, Object> asMap() {
    return ImmutableMap.of(
        "epochLength",
        getEpochLength(),
        "blockPeriodSeconds",
        getBlockPeriodSeconds(),
        "createemptyblocks",
        getCreateEmptyBlocks());
  }
}
