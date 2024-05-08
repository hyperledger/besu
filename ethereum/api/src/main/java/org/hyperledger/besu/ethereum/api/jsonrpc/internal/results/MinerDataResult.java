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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import java.util.List;

import org.immutables.value.Value;

/** The type Miner data result. */
@Value.Immutable
public abstract class MinerDataResult implements JsonRpcResult {
  /** Default constructor. */
  public MinerDataResult() {}

  /**
   * Gets net block reward.
   *
   * @return the net block reward
   */
  public abstract String getNetBlockReward();

  /**
   * Gets static block reward.
   *
   * @return the static block reward
   */
  public abstract String getStaticBlockReward();

  /**
   * Gets transaction fee.
   *
   * @return the transaction fee
   */
  public abstract String getTransactionFee();

  /**
   * Gets uncle inclusion reward.
   *
   * @return the uncle inclusion reward
   */
  public abstract String getUncleInclusionReward();

  /**
   * Gets uncle rewards.
   *
   * @return the uncle rewards
   */
  public abstract List<UncleRewardResult> getUncleRewards();

  /**
   * Gets coinbase.
   *
   * @return the coinbase
   */
  public abstract String getCoinbase();

  /**
   * Gets extra data.
   *
   * @return the extra data
   */
  public abstract String getExtraData();

  /**
   * Gets difficulty.
   *
   * @return the difficulty
   */
  public abstract String getDifficulty();

  /**
   * Gets total difficulty.
   *
   * @return the total difficulty
   */
  public abstract String getTotalDifficulty();

  /** The interface Uncle reward result. */
  @Value.Immutable
  public interface UncleRewardResult {
    /**
     * Gets hash.
     *
     * @return the hash
     */
    String getHash();

    /**
     * Gets coinbase.
     *
     * @return the coinbase
     */
    String getCoinbase();
  }
}
