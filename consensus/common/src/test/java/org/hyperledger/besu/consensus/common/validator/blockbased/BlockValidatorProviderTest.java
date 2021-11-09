/*
 * Copyright Hyperledger Besu contributors.
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
package org.hyperledger.besu.consensus.common.validator.blockbased;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.SoftAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BlockValidatorProviderTest {

  @Mock private BlockInterface blockInterface;
  @Mock private Blockchain blockchain;
  @Mock private EpochManager epochManager;

  @Test
  public void nonForkingValidatorProviderHasNoOverrides() {
    final BlockValidatorProvider blockValidatorProvider =
        BlockValidatorProvider.nonForkingValidatorProvider(
            blockchain, epochManager, blockInterface);

    assertThat(blockValidatorProvider.hasValidatorOverridesForBlockNumber(0)).isFalse();
  }

  @Test
  public void forkingValidatorProviderHasNoOverrides() {
    final BlockValidatorProvider blockValidatorProvider =
        BlockValidatorProvider.forkingValidatorProvider(
            blockchain, epochManager, blockInterface, new BftValidatorOverrides(emptyMap()));

    assertThat(blockValidatorProvider.hasValidatorOverridesForBlockNumber(0)).isFalse();
  }

  @Test
  public void forkingValidatorProviderHasOverridesForBlock1() {
    final Map<Long, List<Address>> overriddenValidators =
        Map.of(1L, List.of(Address.fromHexString("0")));
    final BftValidatorOverrides bftValidatorOverrides =
        new BftValidatorOverrides(overriddenValidators);
    final BlockValidatorProvider blockValidatorProvider =
        BlockValidatorProvider.forkingValidatorProvider(
            blockchain, epochManager, blockInterface, bftValidatorOverrides);

    SoftAssertions.assertSoftly(
        (softly) -> {
          softly
              .assertThat(blockValidatorProvider.hasValidatorOverridesForBlockNumber(0))
              .as("Block 0 should have no overridden validators")
              .isFalse();
          softly
              .assertThat(blockValidatorProvider.hasValidatorOverridesForBlockNumber(1))
              .as("Block 1 should have some overridden validators")
              .isTrue();
          softly
              .assertThat(blockValidatorProvider.hasValidatorOverridesForBlockNumber(2))
              .as("Block 2 should have no overridden validators")
              .isFalse();
        });
  }
}
