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

import java.util.Optional;
import java.util.OptionalInt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The Clique fork. */
public class CliqueFork implements Fork {

  /** The constant FORK_BLOCK_KEY. */
  public static final String FORK_BLOCK_KEY = "block";

  /** The constant BLOCK_PERIOD_SECONDS_KEY. */
  public static final String BLOCK_PERIOD_SECONDS_KEY = "blockperiodseconds";

  /** The constant CREATE_EMPTY_BLOCKS_KEY. */
  public static final String CREATE_EMPTY_BLOCKS_KEY = "createemptyblocks";

  /** The Fork config root. */
  protected final ObjectNode forkConfigRoot;

  /**
   * Instantiates a new Clique fork.
   *
   * @param forkConfigRoot the fork config root
   */
  @JsonCreator
  public CliqueFork(final ObjectNode forkConfigRoot) {
    this.forkConfigRoot = forkConfigRoot;
  }

  /**
   * Gets fork block.
   *
   * @return the fork block
   */
  @Override
  public long getForkBlock() {
    return JsonUtil.getLong(forkConfigRoot, FORK_BLOCK_KEY)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Fork block not specified for Clique fork in custom forks"));
  }

  /**
   * Gets block period seconds.
   *
   * @return the block period seconds
   */
  public OptionalInt getBlockPeriodSeconds() {
    return JsonUtil.getPositiveInt(forkConfigRoot, BLOCK_PERIOD_SECONDS_KEY);
  }

  /**
   * Gets create empty blocks.
   *
   * @return the create empty blocks
   */
  public Optional<Boolean> getCreateEmptyBlocks() {
    return JsonUtil.getBoolean(forkConfigRoot, CREATE_EMPTY_BLOCKS_KEY);
  }
}
