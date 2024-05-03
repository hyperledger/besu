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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

public class WithdrawalsValidatorTest {
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @Test
  public void validateProhibitedWithdrawals() {
    assertThat(
            new WithdrawalsValidator.ProhibitedWithdrawals().validateWithdrawals(Optional.empty()))
        .isTrue();
  }

  @Test
  public void validateProhibitedWithdrawalsRoot() {
    final Block block = blockDataGenerator.block();
    assertThat(new WithdrawalsValidator.ProhibitedWithdrawals().validateWithdrawalsRoot(block))
        .isTrue();
  }

  @Test
  public void invalidateProhibitedWithdrawals() {
    assertThat(
            new WithdrawalsValidator.ProhibitedWithdrawals()
                .validateWithdrawals(Optional.of(emptyList())))
        .isFalse();
  }

  @Test
  public void invalidateProhibitedWithdrawalsRoot() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setWithdrawalsRoot(Hash.EMPTY_LIST_HASH);
    final Block block = blockDataGenerator.block(blockOptions);
    assertThat(new WithdrawalsValidator.ProhibitedWithdrawals().validateWithdrawalsRoot(block))
        .isFalse();
  }

  @Test
  public void validateAllowedWithdrawals() {
    assertThat(
            new WithdrawalsValidator.AllowedWithdrawals()
                .validateWithdrawals(Optional.of(emptyList())))
        .isTrue();
  }

  @Test
  public void validateAllowedWithdrawalsRoot() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawals(Optional.of(Collections.emptyList()))
            .setWithdrawalsRoot(Hash.EMPTY_TRIE_HASH);
    final Block block = blockDataGenerator.block(blockOptions);
    assertThat(new WithdrawalsValidator.AllowedWithdrawals().validateWithdrawalsRoot(block))
        .isTrue();
  }

  @Test
  public void invalidateAllowedWithdrawals() {
    assertThat(new WithdrawalsValidator.AllowedWithdrawals().validateWithdrawals(Optional.empty()))
        .isFalse();
  }

  @Test
  public void invalidateAllowedWithdrawalsRoot() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setWithdrawals(Optional.of(Collections.emptyList()))
            .setWithdrawalsRoot(Hash.ZERO); // this is invalid it should be empty trie hash
    final Block block = blockDataGenerator.block(blockOptions);
    assertThat(new WithdrawalsValidator.AllowedWithdrawals().validateWithdrawalsRoot(block))
        .isFalse();
  }
}
