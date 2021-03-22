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
package org.hyperledger.besu.consensus.qbft;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.Vote;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an BFT consensus mechanism.
 */
public class QbftExtraDataCodec extends BftExtraDataCodec {

  public static Bytes encodeFromAddresses(final Collection<Address> addresses) {
    return new QbftExtraDataCodec()
        .encode(
            new BftExtraData(
                Bytes.wrap(new byte[EXTRA_VANITY_LENGTH]),
                Collections.emptyList(),
                Optional.empty(),
                0,
                addresses));
  }

  public static String createGenesisExtraDataString(final List<Address> validators) {
    return encodeFromAddresses(validators).toString();
  }

  @Override
  public BftExtraData decodeRaw(final Bytes input) {
    if (input.isEmpty()) {
      throw new IllegalArgumentException("Invalid Bytes supplied - Bft Extra Data required.");
    }

    final RLPInput rlpInput = new BytesValueRLPInput(input, false);

    rlpInput.enterList(); // This accounts for the "root node" which contains BFT data items.
    final Bytes vanityData = rlpInput.readBytes();
    final List<Address> validators = rlpInput.readList(Address::readFrom);

    final Optional<Vote> vote;
    if (rlpInput.nextIsList() && rlpInput.nextSize() == 0) {
      vote = Optional.empty();
      rlpInput.skipNext();
    } else {
      vote = Optional.of(Vote.readFrom(rlpInput));
    }

    final int round = rlpInput.readIntScalar();
    final List<SECPSignature> seals =
        rlpInput.readList(
            rlp -> SignatureAlgorithmFactory.getInstance().decodeSignature(rlp.readBytes()));
    rlpInput.leaveList();

    return new BftExtraData(vanityData, seals, vote, round, validators);
  }

  @Override
  protected Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType) {
    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(bftExtraData.getVanityData());
    encoder.writeList(bftExtraData.getValidators(), (validator, rlp) -> rlp.writeBytes(validator));

    if (bftExtraData.getVote().isPresent()) {
      bftExtraData.getVote().get().writeTo(encoder);
    } else {
      encoder.writeList(Collections.emptyList(), (o, rlpOutput) -> {});
    }

    if (encodingType != EncodingType.EXCLUDE_COMMIT_SEALS_AND_ROUND_NUMBER) {
      encoder.writeIntScalar(bftExtraData.getRound());
      if (encodingType != EncodingType.EXCLUDE_COMMIT_SEALS) {
        encoder.writeList(
            bftExtraData.getSeals(), (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
      } else {
        encoder.writeEmptyList();
      }
    } else {
      encoder.writeIntScalar(0);
      encoder.writeEmptyList();
    }
    encoder.endList();

    return encoder.encoded();
  }
}
