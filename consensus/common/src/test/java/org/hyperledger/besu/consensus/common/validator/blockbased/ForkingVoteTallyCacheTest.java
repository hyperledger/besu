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
package org.hyperledger.besu.consensus.common.validator.blockbased;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.consensus.common.BftValidatorOverrides;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.datatypes.Address;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

public class ForkingVoteTallyCacheTest extends VoteTallyCacheTestBase {

  @Test
  public void validatorFromForkAreReturnedRatherThanPriorBlock() {
    final List<Address> forkedValidators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final Map<Long, List<Address>> forkingValidatorMap = new HashMap<>();
    forkingValidatorMap.put(3L, forkedValidators);

    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final ForkingVoteTallyCache cache =
        new ForkingVoteTallyCache(
            blockChain,
            tallyUpdater,
            new EpochManager(30_000),
            blockInterface,
            new BftValidatorOverrides(forkingValidatorMap));

    final VoteTally result = cache.getVoteTallyAfterBlock(block_2.getHeader());

    assertThat(result.getValidators()).containsExactlyElementsOf(forkedValidators);
  }

  @Test
  public void emptyForkingValidatorMapResultsInValidatorsBeingReadFromPreviousHeader() {
    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final ForkingVoteTallyCache cache =
        new ForkingVoteTallyCache(
            blockChain,
            tallyUpdater,
            new EpochManager(30_000),
            blockInterface,
            new BftValidatorOverrides(new HashMap<>()));

    final VoteTally result = cache.getVoteTallyAfterBlock(block_2.getHeader());

    assertThat(result.getValidators()).containsExactlyElementsOf(validators);
  }

  @Test
  public void validatorsInForkUsedIfForkDirectlyFollowsEpoch() {
    final List<Address> forkedValidators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final Map<Long, List<Address>> forkingValidatorMap = new HashMap<>();
    forkingValidatorMap.put(3L, forkedValidators);

    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final ForkingVoteTallyCache cache =
        new ForkingVoteTallyCache(
            blockChain,
            tallyUpdater,
            new EpochManager(2L),
            blockInterface,
            new BftValidatorOverrides(forkingValidatorMap));

    final VoteTally result = cache.getVoteTallyAfterBlock(block_2.getHeader());

    assertThat(result.getValidators()).containsExactlyElementsOf(forkedValidators);
  }

  @Test
  public void atHeadApiOperatesIdenticallyToUnderlyingApi() {
    final List<Address> forkedValidators =
        Lists.newArrayList(Address.fromHexString("5"), Address.fromHexString("6"));
    final Map<Long, List<Address>> forkingValidatorMap = new HashMap<>();
    forkingValidatorMap.put(3L, forkedValidators);

    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final ForkingVoteTallyCache cache =
        new ForkingVoteTallyCache(
            blockChain,
            tallyUpdater,
            new EpochManager(30_000L),
            blockInterface,
            new BftValidatorOverrides(forkingValidatorMap));

    final VoteTally result = cache.getVoteTallyAtHead();

    assertThat(result.getValidators()).containsExactlyElementsOf(forkedValidators);
  }
}
