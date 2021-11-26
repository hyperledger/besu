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
package org.hyperledger.besu.evm.toy;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ToyBlockValues implements BlockValues {

  @Override
  public Bytes getDifficultyBytes() {
    return UInt256.ZERO;
  }

  @Override
  public Optional<Wei> getBaseFee() {
    return Optional.empty();
  }

  @Override
  public long getNumber() {
    return 0;
  }

  @Override
  public long getGasLimit() {
    return 0;
  }

  @Override
  public long getTimestamp() {
    return 0;
  }
}
