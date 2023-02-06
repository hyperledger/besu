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

package org.hyperledger.besu.evm.internal;

/** The Evm configuration. */
public class EvmConfiguration {
  /** The constant DEFAULT. */
  public static final EvmConfiguration DEFAULT = new EvmConfiguration(32_000L);

  private final long jumpDestCacheWeightKB;

  /**
   * Instantiates a new Evm configuration.
   *
   * @param jumpDestCacheWeightKB the jump dest cache weight kb
   */
  public EvmConfiguration(final long jumpDestCacheWeightKB) {
    this.jumpDestCacheWeightKB = jumpDestCacheWeightKB;
  }

  /**
   * Gets jump dest cache weight bytes.
   *
   * @return the jump dest cache weight bytes
   */
  public long getJumpDestCacheWeightBytes() {
    return jumpDestCacheWeightKB * 1024L;
  }

  /**
   * Gets jump dest cache weight kb.
   *
   * @return the jump dest cache weight kb
   */
  public long getJumpDestCacheWeightKB() {
    return jumpDestCacheWeightKB;
  }
}
