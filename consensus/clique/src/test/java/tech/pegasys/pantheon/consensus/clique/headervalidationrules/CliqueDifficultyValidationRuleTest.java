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
package tech.pegasys.pantheon.consensus.clique.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.consensus.clique.TestHelpers.createCliqueSignedBlockHeader;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

public class CliqueDifficultyValidationRuleTest {

  private final KeyPair proposerKeyPair = KeyPair.generate();
  private final List<Address> validatorList = Lists.newArrayList();
  private ProtocolContext<CliqueContext> cliqueProtocolContext;
  private BlockHeaderTestFixture blockHeaderBuilder;

  @Before
  public void setup() {
    final Address localAddress = Util.publicKeyToAddress(proposerKeyPair.getPublicKey());
    validatorList.add(localAddress);
    validatorList.add(AddressHelpers.calculateAddressWithRespectTo(localAddress, 1));

    final VoteTallyCache voteTallyCache = mock(VoteTallyCache.class);
    when(voteTallyCache.getVoteTallyAfterBlock(any())).thenReturn(new VoteTally(validatorList));
    final VoteProposer voteProposer = new VoteProposer();

    final CliqueContext cliqueContext = new CliqueContext(voteTallyCache, voteProposer, null);
    cliqueProtocolContext = new ProtocolContext<>(null, null, cliqueContext);
    blockHeaderBuilder = new BlockHeaderTestFixture();
  }

  @Test
  public void isTrueIfInTurnValidatorSuppliesDifficultyOfTwo() {
    final long IN_TURN_BLOCK_NUMBER = validatorList.size(); // i.e. proposer is 'in turn'
    final UInt256 REPORTED_DIFFICULTY = UInt256.of(2);

    blockHeaderBuilder.number(IN_TURN_BLOCK_NUMBER - 1L);
    final BlockHeader parentHeader =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    blockHeaderBuilder.number(IN_TURN_BLOCK_NUMBER).difficulty(REPORTED_DIFFICULTY);
    final BlockHeader newBlock =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    final CliqueDifficultyValidationRule diffValidationRule = new CliqueDifficultyValidationRule();
    assertThat(diffValidationRule.validate(newBlock, parentHeader, cliqueProtocolContext)).isTrue();
  }

  @Test
  public void isTrueIfOutTurnValidatorSuppliesDifficultyOfOne() {
    final long OUT_OF_TURN_BLOCK_NUMBER = validatorList.size() - 1L;
    final UInt256 REPORTED_DIFFICULTY = UInt256.of(1);

    blockHeaderBuilder.number(OUT_OF_TURN_BLOCK_NUMBER - 1L);
    final BlockHeader parentHeader =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    blockHeaderBuilder.number(OUT_OF_TURN_BLOCK_NUMBER).difficulty(REPORTED_DIFFICULTY);
    final BlockHeader newBlock =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    final CliqueDifficultyValidationRule diffValidationRule = new CliqueDifficultyValidationRule();
    assertThat(diffValidationRule.validate(newBlock, parentHeader, cliqueProtocolContext)).isTrue();
  }

  @Test
  public void isFalseIfOutTurnValidatorSuppliesDifficultyOfTwo() {
    final long OUT_OF_TURN_BLOCK_NUMBER = validatorList.size() - 1L;
    final UInt256 REPORTED_DIFFICULTY = UInt256.of(2);

    blockHeaderBuilder.number(OUT_OF_TURN_BLOCK_NUMBER - 1L);
    final BlockHeader parentHeader =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    blockHeaderBuilder.number(OUT_OF_TURN_BLOCK_NUMBER).difficulty(REPORTED_DIFFICULTY);
    final BlockHeader newBlock =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    final CliqueDifficultyValidationRule diffValidationRule = new CliqueDifficultyValidationRule();
    assertThat(diffValidationRule.validate(newBlock, parentHeader, cliqueProtocolContext))
        .isFalse();
  }

  @Test
  public void isFalseIfInTurnValidatorSuppliesDifficultyOfOne() {
    final long IN_TURN_BLOCK_NUMBER = validatorList.size();
    final UInt256 REPORTED_DIFFICULTY = UInt256.of(1);

    blockHeaderBuilder.number(IN_TURN_BLOCK_NUMBER - 1L);
    final BlockHeader parentHeader =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    blockHeaderBuilder.number(IN_TURN_BLOCK_NUMBER).difficulty(REPORTED_DIFFICULTY);
    final BlockHeader newBlock =
        createCliqueSignedBlockHeader(blockHeaderBuilder, proposerKeyPair, validatorList);

    final CliqueDifficultyValidationRule diffValidationRule = new CliqueDifficultyValidationRule();
    assertThat(diffValidationRule.validate(newBlock, parentHeader, cliqueProtocolContext))
        .isFalse();
  }
}
