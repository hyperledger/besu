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
package tech.pegasys.pantheon.consensus.clique;

import tech.pegasys.pantheon.consensus.common.CastVote;
import tech.pegasys.pantheon.consensus.common.ValidatorVotePolarity;
import tech.pegasys.pantheon.consensus.common.VoteBlockInterface;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableBiMap;

public class CliqueVotingBlockInterface implements VoteBlockInterface {

  public static final Address NO_VOTE_SUBJECT =
      Address.wrap(BytesValue.wrap(new byte[Address.SIZE]));

  private static final ImmutableBiMap<ValidatorVotePolarity, Long> voteToValue =
      ImmutableBiMap.of(
          ValidatorVotePolarity.ADD, 0xFFFFFFFFFFFFFFFFL,
          ValidatorVotePolarity.DROP, 0x0L);

  @Override
  public Optional<CastVote> extractVoteFromHeader(final BlockHeader header) {
    final Address candidate = header.getCoinbase();
    if (!candidate.equals(NO_VOTE_SUBJECT)) {
      final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(header.getExtraData());
      final Address proposer = CliqueBlockHashing.recoverProposerAddress(header, cliqueExtraData);
      final ValidatorVotePolarity votePolarity = voteToValue.inverse().get(header.getNonce());
      final Address recipient = header.getCoinbase();

      return Optional.of(new CastVote(votePolarity, proposer, recipient));
    }
    return Optional.empty();
  }

  @Override
  public BlockHeaderBuilder insertVoteToHeaderBuilder(
      final BlockHeaderBuilder builder, final Optional<CastVote> vote) {
    if (vote.isPresent()) {
      final CastVote voteToCast = vote.get();
      builder.nonce(voteToValue.get(voteToCast.getVotePolarity()));
      builder.coinbase(voteToCast.getRecipient());
    } else {
      builder.nonce(voteToValue.get(ValidatorVotePolarity.DROP));
      builder.coinbase(NO_VOTE_SUBJECT);
    }
    return builder;
  }

  @Override
  public List<Address> validatorsInBlock(final BlockHeader header) {
    return CliqueExtraData.decode(header.getExtraData()).getValidators();
  }
}
