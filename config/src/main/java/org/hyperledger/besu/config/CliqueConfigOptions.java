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

import org.immutables.value.Value;

/** Configuration options for the Clique consensus mechanism. */
@Value.Immutable
public interface CliqueConfigOptions {

  /**
   * The number of blocks in an epoch.
   *
   * @return the epoch length
   */
  long getEpochLength();

  /**
   * Gets block period seconds.
   *
   * @return the block period seconds
   */
  int getBlockPeriodSeconds();

  /**
   * Gets create empty blocks.
   *
   * @return whether empty blocks are permitted
   */
  boolean getCreateEmptyBlocks();

  /**
   * A map of the config options.
   *
   * @return the map
   */
  Map<String, Object> asMap();
}
