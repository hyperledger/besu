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
package org.hyperledger.besu.consensus.qbft.pki;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;

/*
 The PkiQbftExtraData encoding format is different from the "regular" QbftExtraData encoding.
 We have an "envelope" list, with two elements: the extra data and the cms message.
 The RLP encoding format is as follows: ["extra_data" || "cms"]
*/
public class PkiQbftExtraDataCodec extends QbftExtraDataCodec {

  @Override
  public BftExtraData decodeRaw(final Bytes input) {
    if (input.isEmpty()) {
      throw new IllegalArgumentException("Invalid Bytes supplied - Bft Extra Data required.");
    }

    final BftExtraData bftExtraData = super.decodeRaw(input);

    final RLPInput rlpInput = new BytesValueRLPInput(input, false);
    rlpInput.enterList();
    rlpInput.skipNext();
    rlpInput.skipNext();
    rlpInput.skipNext();
    rlpInput.skipNext();
    rlpInput.skipNext();
    final Bytes cms;
    if (!rlpInput.isEndOfCurrentList()) {
      cms = rlpInput.readBytes();
    } else {
      cms = Bytes.EMPTY;
    }

    return new PkiQbftExtraData(bftExtraData, cms);
  }

  @Override
  protected Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType) {
    final PkiQbftExtraData extraData;
    if (!(bftExtraData instanceof PkiQbftExtraData)) {
      return super.encode(bftExtraData, encodingType);
    } else {
      extraData = (PkiQbftExtraData) bftExtraData;
    }

    // TODO-lucas Instead of overriding the QbftExtraDataCodec method, we should just just do the
    // extra
    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeBytes(bftExtraData.getVanityData());
    encoder.writeList(bftExtraData.getValidators(), (validator, rlp) -> rlp.writeBytes(validator));

    if (bftExtraData.getVote().isPresent()) {
      encodeVote(encoder, bftExtraData.getVote().get());
    } else {
      encoder.writeList(Collections.emptyList(), (o, rlpOutput) -> {});
    }

    // When encoding w/o CMS, we don't need 'commit seals' or 'round number'

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

    if (encodingType != EncodingType.EXCLUDE_CMS) {
      encoder.writeBytes(extraData.getCms());
    } else {
      encoder.writeBytes(Bytes.EMPTY);
    }

    encoder.endList();

    return encoder.encoded();
  }
}
