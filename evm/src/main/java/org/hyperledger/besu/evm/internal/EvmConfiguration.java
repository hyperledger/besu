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

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The type Evm configuration.
 *
 * @param jumpDestCacheWeightKB the jump destination cache weight in kb
 * @param worldUpdaterMode the world updater mode
 * @param maxCodeSizeOverride An optional override of the maximum code size set by the EVM fork
 * @param maxInitcodeSizeOverride An optional override of the maximum initcode size set by the EVM
 *     fork
 * @param evmStackSizeOverride the maximum evm stack size
 */
public record EvmConfiguration(
    long jumpDestCacheWeightKB,
    WorldUpdaterMode worldUpdaterMode,
    Optional<Integer> maxCodeSizeOverride,
    Optional<Integer> maxInitcodeSizeOverride,
    Optional<Integer> evmStackSizeOverride) {

  /** How should the world state update be handled within transactions? */
  public enum WorldUpdaterMode {
    /**
     * Stack updates, requiring original account and storage values to read through the whole stack
     */
    STACKED,
    /** Share a single state for accounts and storage values, undoing changes on reverts. */
    JOURNALED
  }

  /** The constant DEFAULT. */
  public static final EvmConfiguration DEFAULT =
      new EvmConfiguration(32_000L, WorldUpdaterMode.STACKED);

  /**
   * Create an EVM Configuration without any overrides
   *
   * @param jumpDestCacheWeightKilobytes the jump dest cache weight (in kibibytes)
   * @param worldstateUpdateMode the workd update mode
   */
  public EvmConfiguration(
      final Long jumpDestCacheWeightKilobytes, final WorldUpdaterMode worldstateUpdateMode) {
    this(
        jumpDestCacheWeightKilobytes,
        worldstateUpdateMode,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
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
   * Update the configuration with new overrides, or clearing the overrides with {@link
   * Optional#empty}
   *
   * @param newMaxCodeSize a new max code size override
   * @param newMaxInitcodeSize a new max initcode size override
   * @param newEvmStackSize a new EVM stack size override
   * @return the updated EVM configuration
   */
  public EvmConfiguration overrides(
      final OptionalInt newMaxCodeSize,
      final OptionalInt newMaxInitcodeSize,
      final OptionalInt newEvmStackSize) {
    return new EvmConfiguration(
        jumpDestCacheWeightKB,
        worldUpdaterMode,
        newMaxCodeSize.isPresent() ? Optional.of(newMaxCodeSize.getAsInt()) : Optional.empty(),
        newMaxInitcodeSize.isPresent()
            ? Optional.of(newMaxInitcodeSize.getAsInt())
            : Optional.empty(),
        newEvmStackSize.isPresent() ? Optional.of(newEvmStackSize.getAsInt()) : Optional.empty());
  }
}
