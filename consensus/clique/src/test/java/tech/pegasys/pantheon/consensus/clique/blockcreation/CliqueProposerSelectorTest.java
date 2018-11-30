/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.clique.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.clique.TestHelpers;
import tech.pegasys.pantheon.consensus.clique.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Util;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueProposerSelectorTest {

  private final KeyPair proposerKey = KeyPair.generate();
  private final Address proposerAddress = Util.publicKeyToAddress(proposerKey.getPublicKey());

  private final List<Address> validatorList =
      Lists.newArrayList(
          AddressHelpers.calculateAddressWithRespectTo(proposerAddress, -1),
          proposerAddress,
          AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 1),
          AddressHelpers.calculateAddressWithRespectTo(proposerAddress, 2));
  private final VoteTally voteTally = new VoteTally(validatorList);
  private VoteTallyCache voteTallyCache;
  private final BlockHeaderTestFixture headerBuilderFixture = new BlockHeaderTestFixture();

  @Before
  public void setup() {
    voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(voteTally);

    headerBuilderFixture.number(2);
  }

  @Test
  public void firstBlockAfterGenesisIsTheSecondValidator() {
    final BlockHeaderTestFixture headerBuilderFixture = new BlockHeaderTestFixture();
    final CliqueProposerSelector selector = new CliqueProposerSelector(voteTallyCache);
    headerBuilderFixture.number(0);

    assertThat(selector.selectProposerForNextBlock(headerBuilderFixture.buildHeader()))
        .isEqualTo(validatorList.get(1));
  }

  @Test
  public void selectsNextProposerInValidatorSet() {
    final BlockHeader parentHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilderFixture, proposerKey, validatorList);
    final CliqueProposerSelector selector = new CliqueProposerSelector(voteTallyCache);

    // Proposer is at index 1, so the next proposer is at index 2
    assertThat(selector.selectProposerForNextBlock(parentHeader)).isEqualTo(validatorList.get(2));
  }

  @Test
  public void selectsNextIndexWhenProposerIsNotInValidatorsForBlock() {
    final BlockHeader parentHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilderFixture, proposerKey, validatorList);
    final CliqueProposerSelector selector = new CliqueProposerSelector(voteTallyCache);

    validatorList.remove(proposerAddress);

    // As the proposer was removed (index 1), the next proposer should also be index 1
    assertThat(selector.selectProposerForNextBlock(parentHeader)).isEqualTo(validatorList.get(1));
  }

  @Test
  public void singleValidatorFindsItselfAsNextProposer() {
    final List<Address> localValidators = Lists.newArrayList(proposerAddress);
    final VoteTally localVoteTally = new VoteTally(localValidators);
    when(voteTallyCache.getVoteTallyAtBlock(any())).thenReturn(localVoteTally);

    final BlockHeader parentHeader =
        TestHelpers.createCliqueSignedBlockHeader(headerBuilderFixture, proposerKey, validatorList);
    final CliqueProposerSelector selector = new CliqueProposerSelector(voteTallyCache);

    assertThat(selector.selectProposerForNextBlock(parentHeader)).isEqualTo(proposerAddress);
  }
}
