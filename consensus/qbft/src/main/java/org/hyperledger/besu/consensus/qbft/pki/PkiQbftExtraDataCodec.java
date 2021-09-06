/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
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

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/*
 The PkiQbftExtraData encoding format is different from the "regular" QbftExtraData encoding. We
 have an extra bytes element in the end of the list.
*/
public class PkiQbftExtraDataCodec extends QbftExtraDataCodec {

  public static final int QBFT_EXTRA_DATA_LIST_SIZE = 5;

  @Override
  public BftExtraData decodeRaw(final Bytes input) {
    if (input.isEmpty()) {
      throw new IllegalArgumentException("Invalid Bytes supplied - Bft Extra Data required.");
    }

    final BftExtraData bftExtraData = super.decodeRaw(input);

    final RLPInput rlpInput = new BytesValueRLPInput(input, false);

    final Bytes cms;
    final List<RLPInput> elements = rlpInput.readList(RLPInput::readAsRlp);
    if (elements.size() > QBFT_EXTRA_DATA_LIST_SIZE) {
      final RLPInput cmsElement = elements.get(elements.size() - 1);
      cms = cmsElement.readBytes();
    } else {
      cms = Bytes.EMPTY;
    }

    return new PkiQbftExtraData(bftExtraData, cms);
  }

  @Override
  protected Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType) {
    return encode(bftExtraData, encodingType, true);
  }

  private Bytes encode(
      final BftExtraData bftExtraData, final EncodingType encodingType, final boolean includeCms) {
    final Bytes encoded = super.encode(bftExtraData, encodingType);
    if (!(bftExtraData instanceof PkiQbftExtraData) || !includeCms) {
      return encoded;
    }

    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    // Read through extraData RLP list elements and write them to the new RLP output
    new BytesValueRLPInput(encoded, false)
        .readList(RLPInput::readAsRlp).stream()
            .map(RLPInput::raw)
            .forEach(rlpOutput::writeRLPBytes);
    rlpOutput.writeBytes(((PkiQbftExtraData) bftExtraData).getCms());
    rlpOutput.endList();

    return rlpOutput.encoded();
  }

  public Bytes encodeWithoutCms(final BftExtraData bftExtraData) {
    return encode(bftExtraData, EncodingType.ALL, false);
  }
}
