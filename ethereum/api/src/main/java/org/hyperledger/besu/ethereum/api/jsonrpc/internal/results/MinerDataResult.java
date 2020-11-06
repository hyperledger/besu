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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;

public class MinerDataResult implements JsonRpcResult {
  private final String netBlockReward;
  private final String staticBlockReward;
  private final String transactionFee;
  private final String uncleInclusionReward;
  private final List<UncleRewardResult> uncleRewards;
  private final String coinbase;
  private final String extraData;
  private final String difficulty;
  private final String totalDifficulty;

  public MinerDataResult(
      final Wei netBlockReward,
      final Wei staticBlockReward,
      final Wei transactionFee,
      final Wei uncleInclusionReward,
      final Map<Hash, Address> uncleRewards,
      final Address coinbase,
      final Bytes extraData,
      final Difficulty difficulty,
      final Difficulty totalDifficulty) {
    this.netBlockReward = Quantity.create(netBlockReward);
    this.staticBlockReward = Quantity.create(staticBlockReward);
    this.transactionFee = Quantity.create(transactionFee);
    this.uncleInclusionReward = Quantity.create(uncleInclusionReward);
    this.uncleRewards = setUncleRewards(uncleRewards);
    this.coinbase = coinbase.toString();
    this.extraData = extraData.toString();
    this.difficulty = Quantity.create(difficulty);
    this.totalDifficulty = Quantity.create(totalDifficulty);
  }

  public String getNetBlockReward() {
    return netBlockReward;
  }

  public String getStaticBlockReward() {
    return staticBlockReward;
  }

  public String getTransactionFee() {
    return transactionFee;
  }

  public String getUncleInclusionReward() {
    return uncleInclusionReward;
  }

  public List<UncleRewardResult> getUncleRewards() {
    return uncleRewards;
  }

  public String getCoinbase() {
    return coinbase;
  }

  public String getExtraData() {
    return extraData;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public String getTotalDifficulty() {
    return totalDifficulty;
  }

  private List<UncleRewardResult> setUncleRewards(final Map<Hash, Address> uncleRewards) {
    return uncleRewards.entrySet().stream()
        .map(b -> new UncleRewardResult(b.getKey().toString(), b.getValue().toString()))
        .collect(Collectors.toList());
  }

  private static class UncleRewardResult {
    private final String hash;
    private final String coinbase;

    private UncleRewardResult(final String hash, final String coinbase) {
      this.hash = hash;
      this.coinbase = coinbase;
    }

    public String getHash() {
      return hash;
    }

    public String getCoinbase() {
      return coinbase;
    }
  }
}
