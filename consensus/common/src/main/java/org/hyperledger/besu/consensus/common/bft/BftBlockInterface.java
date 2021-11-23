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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.consensus.common.validator.VoteType;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BftBlockInterface implements BlockInterface {

  private final BftExtraDataCodec bftExtraDataCodec;

  public BftBlockInterface(final BftExtraDataCodec bftExtraDataCodec) {
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  @Override
  public Address getProposerOfBlock(final BlockHeader header) {
    return header.getCoinbase();
  }

  @Override
  public Address getProposerOfBlock(final org.hyperledger.besu.plugin.data.BlockHeader header) {
    return Address.fromHexString(header.getCoinbase().toHexString());
  }

  @Override
  public Optional<ValidatorVote> extractVoteFromHeader(final BlockHeader header) {
    final BftExtraData bftExtraData = bftExtraDataCodec.decode(header);

    if (bftExtraData.getVote().isPresent()) {
      final Vote headerVote = bftExtraData.getVote().get();
      final ValidatorVote vote =
          new ValidatorVote(
              headerVote.isAuth() ? VoteType.ADD : VoteType.DROP,
              getProposerOfBlock(header),
              headerVote.getRecipient());
      return Optional.of(vote);
    }
    return Optional.empty();
  }

  @Override
  public Collection<Address> validatorsInBlock(final BlockHeader header) {
    final BftExtraData bftExtraData = bftExtraDataCodec.decode(header);
    return bftExtraData.getValidators();
  }

  public Block replaceRoundInBlock(
      final Block block, final int round, final BlockHeaderFunctions blockHeaderFunctions) {
    final BftExtraData prevExtraData = bftExtraDataCodec.decode(block.getHeader());
    final BftExtraData substituteExtraData =
        new BftExtraData(
            prevExtraData.getVanityData(),
            prevExtraData.getSeals(),
            prevExtraData.getVote(),
            round,
            prevExtraData.getValidators());

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(block.getHeader());
    headerBuilder
        .extraData(bftExtraDataCodec.encode(substituteExtraData))
        .blockHeaderFunctions(blockHeaderFunctions);

    final BlockHeader newHeader = headerBuilder.buildBlockHeader();

    return new Block(newHeader, block.getBody());
  }

  public BftExtraData getExtraData(final BlockHeader header) {
    return bftExtraDataCodec.decode(header);
  }

  public List<Address> getCommitters(final BlockHeader header) {
    final BftExtraData bftExtraData = bftExtraDataCodec.decode(header);

    final Hash committerHash =
        Hash.hash(
            BftBlockHashing.serializeHeader(
                header,
                () -> bftExtraDataCodec.encodeWithoutCommitSeals(bftExtraData),
                bftExtraDataCodec));

    return bftExtraData.getSeals().stream()
        .map(p -> Util.signatureToAddress(p, committerHash))
        .collect(Collectors.toList());
  }
}
