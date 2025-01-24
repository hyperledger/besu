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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Collection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents encoder/decoder of the serialized data structure stored in the extraData field of the
 * BlockHeader used when operating under an IBFT consensus mechanism.
 */
public class IbftExtraDataCodec extends BftExtraDataCodec {
  // private static final Logger LOG = LoggerFactory.getLogger(IbftExtraDataCodec.class);

  /** The constant EXTRA_VANITY_LENGTH. */
  public static final int EXTRA_VANITY_LENGTH = 32;

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  /**
   * Decode raw input and return ibft extra data.
   *
   * @param input the input
   * @return the ibft extra data
   */
  @Override
  public BftExtraData decodeRaw(final Bytes input) {
    checkArgument(
        input.size() > EXTRA_VANITY_LENGTH,
        "Invalid Bytes supplied - too short to produce a valid IBFT Extra Data object.");

    final Bytes vanityData = input.slice(0, EXTRA_VANITY_LENGTH);

    final Bytes rlpData = input.slice(EXTRA_VANITY_LENGTH);
    final RLPInput rlpInput = new BytesValueRLPInput(rlpData, false);

    rlpInput.enterList(); // This accounts for the "root node" which contains IBFT data items.
    final Collection<Address> validators = rlpInput.readList(Address::readFrom);
    final SECPSignature proposerSeal = parseProposerSeal(rlpInput);
    final Collection<SECPSignature> seals =
        rlpInput.readList(rlp -> SIGNATURE_ALGORITHM.get().decodeSignature(rlp.readBytes()));
    rlpInput.leaveList();

    return new BftExtraData(vanityData, seals, proposerSeal, validators);
  }

  private static SECPSignature parseProposerSeal(final RLPInput rlpInput) {
    final Bytes data = rlpInput.readBytes();
    return data.isZero() ? null : SIGNATURE_ALGORITHM.get().decodeSignature(data);
  }

  /**
   * Encode extra data to bytes.
   *
   * @return the bytes
   */
  @Override
  public Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType) {
    throw new UnsupportedOperationException("The encode method is not supported.");
  }
  /*  @Override
  public Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType) {
    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeList(bftExtraData.getValidators(), (validator, rlp) -> rlp.writeBytes(validator));
    if (bftExtraData.getProposerSeal() != null) {
      encoder.writeBytes(bftExtraData.getProposerSeal().encodedBytes());
    } else {
      encoder.writeNull();
    }
    encoder.writeList(bftExtraData.getSeals(), (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    return Bytes.wrap(bftExtraData.getVanityData(), encoder.encoded());
  }*/

}
