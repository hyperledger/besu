/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.chain;

public class ChainPrunerConfiguration {
  public static final ChainPrunerConfiguration DEFAULT =
      new ChainPrunerConfiguration(false, 7200, 256);
  private final boolean chainPruningEnabled;
  private final long chainPruningBlocksRetained;
  private final long chainPruningBlocksFrequency;

  public ChainPrunerConfiguration(
      final boolean chainPruningEnabled,
      final long chainPruningBlocksRetained,
      final long chainPruningBlocksFrequency) {
    this.chainPruningEnabled = chainPruningEnabled;
    this.chainPruningBlocksRetained = chainPruningBlocksRetained;
    this.chainPruningBlocksFrequency = chainPruningBlocksFrequency;
  }

  public long getChainPruningBlocksRetained() {
    return chainPruningBlocksRetained;
  }

  public boolean getChainPruningEnabled() {
    return chainPruningEnabled;
  }

  public long getChainPruningBlocksFrequency() {
    return chainPruningBlocksFrequency;
  }
}
