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
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** A block header capable of being processed. */
public class ProcessableBlockHeader
    implements BlockValues, org.hyperledger.besu.plugin.data.ProcessableBlockHeader {

  protected final Hash parentHash;

  protected final Address coinbase;

  protected final Difficulty difficulty;

  protected final long number;

  protected final long gasLimit;

  // The block creation timestamp (seconds since the unix epoch)
  protected final long timestamp;
  // base fee is included for post EIP-1559 blocks
  protected final Wei baseFee;
  // prevRandao is included for post-merge blocks
  protected final Bytes32 mixHashOrPrevRandao;
  // parentBeaconBlockRoot is included for Cancun
  protected final Bytes32 parentBeaconBlockRoot;

  protected ProcessableBlockHeader(
      final Hash parentHash,
      final Address coinbase,
      final Difficulty difficulty,
      final long number,
      final long gasLimit,
      final long timestamp,
      final Wei baseFee,
      final Bytes32 mixHashOrPrevRandao,
      final Bytes32 parentBeaconBlockRoot) {
    this.parentHash = parentHash;
    this.coinbase = coinbase;
    this.difficulty = difficulty;
    this.number = number;
    this.gasLimit = gasLimit;
    this.timestamp = timestamp;
    this.baseFee = baseFee;
    this.mixHashOrPrevRandao = mixHashOrPrevRandao;
    this.parentBeaconBlockRoot = parentBeaconBlockRoot;
  }

  /**
   * Returns the block parent block hash.
   *
   * @return the block parent block hash
   */
  @Override
  public Hash getParentHash() {
    return parentHash;
  }

  /**
   * Returns the block coinbase address.
   *
   * @return the block coinbase address
   */
  @Override
  public Address getCoinbase() {
    return coinbase;
  }

  /**
   * Returns the block difficulty.
   *
   * @return the block difficulty
   */
  @Override
  public Difficulty getDifficulty() {
    return difficulty;
  }

  /**
   * Returns the block difficulty.
   *
   * @return the block difficulty
   */
  @Override
  public Bytes getDifficultyBytes() {
    return difficulty.getAsBytes32();
  }

  /**
   * Returns the block number.
   *
   * @return the block number
   */
  @Override
  public long getNumber() {
    return number;
  }

  /**
   * Return the block gas limit.
   *
   * @return the block gas limit
   */
  @Override
  public long getGasLimit() {
    return gasLimit;
  }

  /**
   * Return the block timestamp.
   *
   * @return the block timestamp
   */
  @Override
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the basefee of the block.
   *
   * @return the optional long value for base fee
   */
  @Override
  public Optional<Wei> getBaseFee() {
    return Optional.ofNullable(baseFee);
  }

  /**
   * Returns the mixHash before merge, and the prevRandao value after
   *
   * @return the mixHash before merge, and the prevRandao value after
   */
  @Override
  public Bytes32 getMixHashOrPrevRandao() {
    return mixHashOrPrevRandao;
  }

  /**
   * Returns the prevRandao of the block.
   *
   * @return the raw bytes of the prevRandao field
   */
  @Override
  public Optional<Bytes32> getPrevRandao() {
    return Optional.ofNullable(mixHashOrPrevRandao);
  }

  /**
   * Returns the parent beacon block root.
   *
   * @return the parent beacon block root.
   */
  @Override
  public Optional<Bytes32> getParentBeaconBlockRoot() {
    return Optional.ofNullable(parentBeaconBlockRoot);
  }

  public String toLogString() {
    return getNumber() + " (time: " + getTimestamp() + ")";
  }
}
