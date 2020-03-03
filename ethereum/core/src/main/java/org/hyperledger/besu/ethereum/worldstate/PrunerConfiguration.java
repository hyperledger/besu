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
package org.hyperledger.besu.ethereum.worldstate;

public class PrunerConfiguration {
  public static final int DEFAULT_PRUNING_BLOCKS_RETAINED = 1024;
  public static final int DEFAULT_PRUNING_BLOCK_CONFIRMATIONS = 10;

  private final int blocksRetainedBeforeSweeping;
  private final int blockConfirmationsBeforeMarking;

  public PrunerConfiguration(
      final int blockConfirmationsBeforeMarking, final int blocksRetainedBeforeSweeping) {
    this.blockConfirmationsBeforeMarking = blockConfirmationsBeforeMarking;
    this.blocksRetainedBeforeSweeping = blocksRetainedBeforeSweeping;
  }

  public static PrunerConfiguration getDefault() {
    return new PrunerConfiguration(
        DEFAULT_PRUNING_BLOCK_CONFIRMATIONS, DEFAULT_PRUNING_BLOCKS_RETAINED);
  }

  public int getBlocksRetained() {
    return blocksRetainedBeforeSweeping;
  }

  public int getBlockConfirmations() {
    return blockConfirmationsBeforeMarking;
  }
}
