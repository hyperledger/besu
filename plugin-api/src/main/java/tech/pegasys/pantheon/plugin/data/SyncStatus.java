/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugin.data;

import tech.pegasys.pantheon.plugin.Unstable;

@Unstable
public interface SyncStatus {

  /**
   * Get the height of the block at which this synchronization attempt began.
   *
   * @return height of the block at which this synchronization attempt began.
   */
  long getStartingBlock();

  /**
   * Get the height of the last block the synchronizer received
   *
   * @return the height of the last block the synchronizer received
   */
  long getCurrentBlock();

  /**
   * Get the height of the highest known block.
   *
   * @return the height of the highest known block.
   */
  long getHighestBlock();

  /**
   * Checks if the synchronizer is within a default sync tolerance of the highest known block
   *
   * @return true if it is within the tolerance, false otherwise
   */
  boolean inSync();
}
