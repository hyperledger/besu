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

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/*
 The PkiQbftExtraData encoding format is different from the "regular" QbftExtraData encoding.
 We have an "envelope" list, with two elements: the extra data and the cms message.
 The RLP encoding format is as follows: ["extra_data", ["cms"]]
*/
public class PkiQbftExtraDataCodec extends QbftExtraDataCodec {

  @Override
  public BftExtraData decodeRaw(final Bytes input) {
    if (input.isEmpty()) {
      throw new IllegalArgumentException("Invalid Bytes supplied - Bft Extra Data required.");
    }

    final RLPInput rlpInput = new BytesValueRLPInput(input, false);
    rlpInput.enterList();

    // Consume all the ExtraData input from the envelope list, and decode it using the QBFT decoder
    final Bytes extraDataListAsBytes = rlpInput.currentListAsBytes();
    final BftExtraData bftExtraData = super.decodeRaw(extraDataListAsBytes);

    final Optional<Bytes> cms;
    if (rlpInput.nextIsList() && rlpInput.nextSize() == 0) {
      cms = Optional.empty();
    } else {
      rlpInput.enterList();
      cms = Optional.of(rlpInput.readBytes());
      rlpInput.leaveList();
    }

    return new PkiQbftExtraData(bftExtraData, cms);
  }

  @Override
  protected Bytes encode(final BftExtraData bftExtraData, final EncodingType encodingType) {
    if (!(bftExtraData instanceof PkiQbftExtraData)) {
      throw new IllegalStateException(
          "PkiQbftExtraDataCodec must be used only with PkiQbftExtraData");
    }
    final PkiQbftExtraData extraData = (PkiQbftExtraData) bftExtraData;

    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList(); // start envelope list

    final Bytes encodedQbftExtraData = super.encode(bftExtraData, encodingType);
    encoder.writeRaw(encodedQbftExtraData);

    if (encodingType == EncodingType.ALL) {
      if (extraData.getCms().isPresent()) {
        Bytes cmsBytes = extraData.getCms().get();
        encoder.startList();
        encoder.writeBytes(cmsBytes);
        encoder.endList();
      } else {
        encoder.writeEmptyList();
      }
    } else {
      encoder.writeEmptyList();
    }

    encoder.endList(); // end envelope list

    return encoder.encoded();
  }
}
