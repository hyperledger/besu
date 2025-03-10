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
package org.hyperledger.besu.consensus.ibftlegacy;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Collection;
import java.util.Optional;

import com.google.common.collect.ImmutableBiMap;
import org.apache.tuweni.bytes.Bytes;

/** The Ibft legacy block interface. */
public class IbftLegacyBlockInterface extends BftBlockInterface {

  /** The constant NO_VOTE_SUBJECT. */
  public static final Address NO_VOTE_SUBJECT = Address.wrap(Bytes.wrap(new byte[Address.SIZE]));

  /** The constant ADD_NONCE. */
  public static final long ADD_NONCE = 0xFFFFFFFFFFFFFFFFL;

  /** The constant DROP_NONCE. */
  public static final long DROP_NONCE = 0x0L;

  private static final ImmutableBiMap<VoteType, Long> voteToValue =
      ImmutableBiMap.of(
          VoteType.ADD, ADD_NONCE,
          VoteType.DROP, DROP_NONCE);

  private static final IbftExtraDataCodec ibftExtraDataCodec = new IbftExtraDataCodec();

  /**
   * Constructor for IbftLegacyBlockInterface.
   *
   * @param bftExtraDataCodec the codec for BFT extra data
   */
  public IbftLegacyBlockInterface(final BftExtraDataCodec bftExtraDataCodec) {
    super(bftExtraDataCodec);
  }

  @Override
  public Address getProposerOfBlock(final BlockHeader header) {
    final IbftLegacyExtraData ibftExtraData = ibftExtraDataCodec.decode(header);
    return IbftBlockHashing.recoverProposerAddress(header, ibftExtraData);
  }

  @Override
  public Address getProposerOfBlock(final org.hyperledger.besu.plugin.data.BlockHeader header) {
    return getProposerOfBlock(
        BlockHeader.convertPluginBlockHeader(
            header,
            new BftBlockHeaderFunctions(
                h -> new BftBlockHashing(ibftExtraDataCodec).calculateDataHashForCommittedSeal(h),
                ibftExtraDataCodec)));
  }

  @Override
  public Optional<ValidatorVote> extractVoteFromHeader(final BlockHeader header) {
    final Address candidate = header.getCoinbase();
    if (!candidate.equals(NO_VOTE_SUBJECT)) {
      final Address proposer = getProposerOfBlock(header);
      final VoteType votePolarity = voteToValue.inverse().get(header.getNonce());
      final Address recipient = header.getCoinbase();

      return Optional.of(new ValidatorVote(votePolarity, proposer, recipient));
    }
    return Optional.empty();
  }

  @Override
  public Collection<Address> validatorsInBlock(final BlockHeader header) {
    return ibftExtraDataCodec.decode(header).getValidators();
  }

  /**
   * Is valid vote value.
   *
   * @param value the value
   * @return the boolean
   */
  public static boolean isValidVoteValue(final long value) {
    return voteToValue.values().contains(value);
  }
}
