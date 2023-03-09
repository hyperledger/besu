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

package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;

public class DepositsValidatorTest {
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @Test
  public void validateProhibitedDeposits() {
    assertThat(new DepositsValidator.ProhibitedDeposits().validateDeposits(Optional.empty()))
        .isTrue();
  }

  @Test
  public void validateProhibitedDepositsRoot() {
    final Block block = blockDataGenerator.block();
    assertThat(new DepositsValidator.ProhibitedDeposits().validateDepositsRoot(block)).isTrue();
  }

  @Test
  public void invalidateProhibitedDeposits() {
    assertThat(
            new DepositsValidator.ProhibitedDeposits().validateDeposits(Optional.of(emptyList())))
        .isFalse();
  }

  @Test
  public void invalidateProhibitedDepositsRoot() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create().setDepositsRoot(Hash.EMPTY_LIST_HASH);
    final Block block = blockDataGenerator.block(blockOptions);
    assertThat(new DepositsValidator.ProhibitedDeposits().validateDepositsRoot(block)).isFalse();
  }

  @Test
  public void validateAllowedDeposits() {
    // TODO 6110: Validate deposits in AllowedDeposits to be included in next PR
  }

  @Test
  public void validateAllowedDepositsRoot() {
    final BlockDataGenerator.BlockOptions blockOptions =
        BlockDataGenerator.BlockOptions.create()
            .setDeposits(Optional.of(Collections.emptyList()))
            .setDepositsRoot(Hash.EMPTY_TRIE_HASH);
    final Block block = blockDataGenerator.block(blockOptions);
    assertThat(new DepositsValidator.AllowedDeposits().validateDepositsRoot(block)).isTrue();
  }

  @Test
  public void invalidateAllowedDeposits() {
    // TODO 6110: Validate deposits in AllowedDeposits to be included in next PR
  }

  @Test
  public void invalidateAllowedDepositsRoot() {
    // TODO 6110: Validate deposits in AllowedDeposits to be included in next PR
  }
}
