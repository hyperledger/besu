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

@Value.Immutable
public abstract class MinerDataResult implements JsonRpcResult {
  public abstract String getNetBlockReward();

  public abstract String getStaticBlockReward();

  public abstract String getTransactionFee();

  public abstract String getUncleInclusionReward();

  public abstract List<UncleRewardResult> getUncleRewards();

  public abstract String getCoinbase();

  public abstract String getExtraData();

  public abstract String getDifficulty();

  public abstract String getTotalDifficulty();

  @Value.Immutable
  public interface UncleRewardResult {
    String getHash();

    String getCoinbase();
  }
}
