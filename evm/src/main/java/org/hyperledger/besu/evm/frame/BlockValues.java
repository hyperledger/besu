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
 */
package org.hyperledger.besu.evm.frame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Block Header Values used by various EVM Opcodes. This is not a complete BlocHeader, just the
 * values that are returned by various operations.
 */
public interface BlockValues {

  /**
   * Returns the block difficulty.
   *
   * @return the block difficulty
   */
  default Bytes getDifficultyBytes() {
    return null;
  }

  /**
   * Returns the basefee of the block.
   *
   * @return the raw bytes of the extra data field
   */
  default Optional<Long> getBaseFee() {
    return Optional.empty();
  }

  /**
   * Returns the block number.
   *
   * @return the block number
   */
  default long getNumber() {
    return 0L;
  }

  /**
   * Return the block timestamp.
   *
   * @return the block timestamp
   */
  default long getTimestamp() {
    return 0L;
  }

  /**
   * Return the block gas limit.
   *
   * @return the block gas limit
   */
  default long getGasLimit() {
    return 0L;
  }
}
