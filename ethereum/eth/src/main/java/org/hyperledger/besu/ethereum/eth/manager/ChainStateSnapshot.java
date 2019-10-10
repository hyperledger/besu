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

package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.util.uint.UInt256;

public class ChainStateSnapshot implements ChainHeadEstimate {
  private final UInt256 totalDifficulty;
  private final long chainHeight;

  public ChainStateSnapshot(final UInt256 totalDifficulty, final long chainHeight) {
    this.totalDifficulty = totalDifficulty;
    this.chainHeight = chainHeight;
  }

  @Override
  public UInt256 getEstimatedTotalDifficulty() {
    return totalDifficulty;
  }

  @Override
  public long getEstimatedHeight() {
    return chainHeight;
  }
}
