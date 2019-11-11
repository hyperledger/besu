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
  public static final long DEFAULT_PRUNING_BLOCKS_RETAINED = 1024;
  public static final long DEFAULT_PRUNING_BLOCK_CONFIRMATIONS = 10;

  private final long blocksRetainedBeforeSweeping;
  private final long blockConfirmationsBeforeMarking;

  public PrunerConfiguration(
      final long blockConfirmationsBeforeMarking, final long blocksRetainedBeforeSweeping) {
    this.blockConfirmationsBeforeMarking = blockConfirmationsBeforeMarking;
    this.blocksRetainedBeforeSweeping = blocksRetainedBeforeSweeping;
  }

  public static PrunerConfiguration getDefault() {
    return new PrunerConfiguration(
        DEFAULT_PRUNING_BLOCK_CONFIRMATIONS, DEFAULT_PRUNING_BLOCKS_RETAINED);
  }

  public long getBlocksRetained() {
    return blocksRetainedBeforeSweeping;
  }

  public long getBlockConfirmations() {
    return blockConfirmationsBeforeMarking;
  }
}
