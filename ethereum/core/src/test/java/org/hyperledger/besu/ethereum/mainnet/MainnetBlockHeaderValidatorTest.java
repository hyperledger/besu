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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.ProtocolContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link MainnetBlockHeaderValidator}. */
@RunWith(MockitoJUnitRunner.class)
public final class MainnetBlockHeaderValidatorTest {

  @SuppressWarnings("unchecked")
  private final ProtocolContext protocolContext = mock(ProtocolContext.class);

  @Test
  public void validHeaderFrontier() throws Exception {
    final BlockHeaderValidator headerValidator =
        MainnetBlockHeaderValidator.create(PoWHasher.ETHASH_LIGHT)
            .difficultyCalculator(MainnetDifficultyCalculators.FRONTIER)
            .build();
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(300006),
                ValidationTestUtils.readHeader(300005),
                protocolContext,
                HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void validHeaderHomestead() throws Exception {
    final BlockHeaderValidator headerValidator =
        MainnetBlockHeaderValidator.create(PoWHasher.ETHASH_LIGHT)
            .difficultyCalculator(MainnetDifficultyCalculators.HOMESTEAD)
            .build();
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(1200001),
                ValidationTestUtils.readHeader(1200000),
                protocolContext,
                HeaderValidationMode.FULL))
        .isTrue();
  }

  @Test
  public void invalidParentHash() throws Exception {
    final BlockHeaderValidator headerValidator =
        MainnetBlockHeaderValidator.create(PoWHasher.ETHASH_LIGHT)
            .difficultyCalculator(MainnetDifficultyCalculators.FRONTIER)
            .build();
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(1200001),
                ValidationTestUtils.readHeader(4400000),
                protocolContext,
                HeaderValidationMode.FULL))
        .isFalse();
  }

  @Test
  public void validHeaderByzantium() throws Exception {
    final BlockHeaderValidator headerValidator =
        MainnetBlockHeaderValidator.create(PoWHasher.ETHASH_LIGHT)
            .difficultyCalculator(MainnetDifficultyCalculators.BYZANTIUM)
            .build();
    assertThat(
            headerValidator.validateHeader(
                ValidationTestUtils.readHeader(4400001),
                ValidationTestUtils.readHeader(4400000),
                protocolContext,
                HeaderValidationMode.FULL))
        .isTrue();
  }
}
