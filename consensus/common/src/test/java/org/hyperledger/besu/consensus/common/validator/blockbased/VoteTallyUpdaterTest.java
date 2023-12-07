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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class VoteTallyUpdaterTest {

  private static final long EPOCH_LENGTH = 30_000;
  private final VoteTally voteTally = mock(VoteTally.class);
  private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final KeyPair proposerKeyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();
  private final Address proposerAddress =
      Address.extract(Hash.hash(proposerKeyPair.getPublicKey().getEncodedBytes()));
  private final Address subject = Address.fromHexString("007f4a23ca00cd043d25c2888c1aa5688f81a344");
  private final Address validator1 =
      Address.fromHexString("00dae27b350bae20c5652124af5d8b5cba001ec1");

  private final BlockInterface serialiser = mock(BlockInterface.class);

  private final VoteTallyUpdater updater =
      new VoteTallyUpdater(new EpochManager(EPOCH_LENGTH), serialiser);

  @Test
  public void voteTallyUpdatedWithAddVote() {
    when(serialiser.extractVoteFromHeader(any()))
        .thenReturn(Optional.of(new ValidatorVote(VoteType.ADD, proposerAddress, subject)));

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH - 1);
    final BlockHeader header = headerBuilder.buildHeader();

    updater.updateForBlock(header, voteTally);

    verify(voteTally).addVote(new ValidatorVote(VoteType.ADD, proposerAddress, subject));
  }

  @Test
  public void voteTallyNotUpdatedWhenBlockHasNoVoteSubject() {
    when(serialiser.extractVoteFromHeader(any())).thenReturn(Optional.empty());

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH - 1);
    final BlockHeader header = headerBuilder.buildHeader();

    updater.updateForBlock(header, voteTally);

    verifyNoInteractions(voteTally);
  }

  @Test
  public void outstandingVotesDiscardedWhenEpochReached() {
    when(serialiser.extractVoteFromHeader(any())).thenReturn(Optional.empty());

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH);
    final BlockHeader header = headerBuilder.buildHeader();

    updater.updateForBlock(header, voteTally);

    verify(voteTally).discardOutstandingVotes();
    verifyNoMoreInteractions(voteTally);
  }

  @Test
  public void buildVoteTallyByExtractingValidatorsFromGenesisBlock() {
    when(serialiser.validatorsInBlock(any())).thenReturn(asList(subject, validator1));
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(0);
    final BlockHeader header = headerBuilder.buildHeader();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(EPOCH_LENGTH);
    when(blockchain.getBlockHeader(EPOCH_LENGTH)).thenReturn(Optional.of(header));

    final VoteTally voteTally = updater.buildVoteTallyFromBlockchain(blockchain);
    assertThat(voteTally.getValidators()).containsExactly(subject, validator1);
  }

  @Test
  public void buildVoteTallyByExtractingValidatorsFromEpochBlock() {
    when(serialiser.validatorsInBlock(any())).thenReturn(asList(subject, validator1));
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final BlockHeader header = headerBuilder.buildHeader();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(EPOCH_LENGTH);
    when(blockchain.getBlockHeader(EPOCH_LENGTH)).thenReturn(Optional.of(header));

    final VoteTally voteTally = updater.buildVoteTallyFromBlockchain(blockchain);
    assertThat(voteTally.getValidators()).containsExactly(subject, validator1);
  }

  @Test
  public void addVotesFromBlocksAfterMostRecentEpoch() {
    when(serialiser.validatorsInBlock(any())).thenReturn(asList(validator1));
    when(serialiser.extractVoteFromHeader(any()))
        .thenReturn(Optional.of(new ValidatorVote(VoteType.ADD, proposerAddress, subject)));

    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    headerBuilder.number(EPOCH_LENGTH);
    final BlockHeader epochHeader = headerBuilder.buildHeader();

    headerBuilder.number(EPOCH_LENGTH + 1);
    final BlockHeader voteBlockHeader = headerBuilder.buildHeader();

    when(blockchain.getChainHeadBlockNumber()).thenReturn(EPOCH_LENGTH + 1);
    when(blockchain.getBlockHeader(EPOCH_LENGTH)).thenReturn(Optional.of(epochHeader));
    when(blockchain.getBlockHeader(EPOCH_LENGTH + 1)).thenReturn(Optional.of(voteBlockHeader));

    final VoteTally voteTally = updater.buildVoteTallyFromBlockchain(blockchain);
    assertThat(voteTally.getValidators()).containsExactly(subject, validator1);
  }
}
