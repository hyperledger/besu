/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.fluent;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** A concrete BlockValues object that takes all the defaults */
public class SimpleBlockValues implements BlockValues {

  Bytes difficultyBytes = Bytes32.ZERO;
  Bytes32 mixHasOrPrevRandao = Bytes32.ZERO;
  Optional<Wei> baseFee = Optional.empty();
  long number = 1;
  long timeStamp = 1;
  long gasLimit = Long.MAX_VALUE;

  @Override
  public Bytes getDifficultyBytes() {
    return difficultyBytes;
  }

  public void setDifficultyBytes(final Bytes difficultyBytes) {
    this.difficultyBytes = difficultyBytes;
  }

  public Bytes32 getMixHasOrPrevRandao() {
    return mixHasOrPrevRandao;
  }

  public void setMixHasOrPrevRandao(final Bytes32 mixHasOrPrevRandao) {
    this.mixHasOrPrevRandao = mixHasOrPrevRandao;
  }

  @Override
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }

  public void setBaseFee(final Optional<Wei> baseFee) {
    this.baseFee = baseFee;
  }

  @Override
  public long getNumber() {
    return number;
  }

  public void setNumber(final long number) {
    this.number = number;
  }

  public long getTimeStamp() {
    return timeStamp;
  }

  public void setTimeStamp(final long timeStamp) {
    this.timeStamp = timeStamp;
  }

  @Override
  public long getGasLimit() {
    return gasLimit;
  }

  public void setGasLimit(final long gasLimit) {
    this.gasLimit = gasLimit;
  }
}
