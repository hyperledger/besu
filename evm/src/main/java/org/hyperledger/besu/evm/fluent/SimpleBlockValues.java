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
package org.hyperledger.besu.evm.fluent;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** A concrete BlockValues object that takes all the defaults */
public class SimpleBlockValues implements BlockValues {

  Bytes difficultyBytes = Bytes32.ZERO;
  Bytes32 mixHashOrPrevRandao = Bytes32.ZERO;
  Optional<Wei> baseFee = Optional.empty();
  long number = 1;
  long timestamp = 1;
  long gasLimit = Long.MAX_VALUE;

  /** Default constructor. */
  public SimpleBlockValues() {}

  @Override
  public Bytes getDifficultyBytes() {
    return difficultyBytes;
  }

  /**
   * Sets the difficulty of the block
   *
   * @param difficultyBytes the difficulty
   */
  public void setDifficultyBytes(final Bytes difficultyBytes) {
    this.difficultyBytes = difficultyBytes;
  }

  @Override
  public Bytes32 getMixHashOrPrevRandao() {
    return mixHashOrPrevRandao;
  }

  /**
   * sets the mix hash or prevRandao
   *
   * @param mixHashOrPrevRandao new mixHash or prevRandao
   */
  public void setMixHashOrPrevRandao(final Bytes32 mixHashOrPrevRandao) {
    this.mixHashOrPrevRandao = mixHashOrPrevRandao;
  }

  @Override
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }

  /**
   * Sets the base fee
   *
   * @param baseFee new base fee, or empty if not in a fee market fork.
   */
  public void setBaseFee(final Optional<Wei> baseFee) {
    this.baseFee = baseFee;
  }

  @Override
  public long getNumber() {
    return number;
  }

  /**
   * Sets the block number
   *
   * @param number the block number
   */
  public void setNumber(final long number) {
    this.number = number;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Sets the block timestamp
   *
   * @param timestamp the timestamp, in seconds past the unix epoch
   */
  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public long getGasLimit() {
    return gasLimit;
  }

  /**
   * Sets the gas limit
   *
   * @param gasLimit the gas limit for the block
   */
  public void setGasLimit(final long gasLimit) {
    this.gasLimit = gasLimit;
  }
}
