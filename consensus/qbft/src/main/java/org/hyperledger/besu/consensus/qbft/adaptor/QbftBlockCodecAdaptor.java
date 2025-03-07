/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlock;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCodec;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/** Adaptor class to allow a {@link QbftExtraDataCodec} to be used as a {@link QbftBlockCodec}. */
public class QbftBlockCodecAdaptor implements QbftBlockCodec {

  private final QbftExtraDataCodec qbftExtraDataCodec;

  /**
   * Constructs a new Qbft block codec.
   *
   * @param qbftExtraDataCodec the QBFT extra data codec
   */
  public QbftBlockCodecAdaptor(final QbftExtraDataCodec qbftExtraDataCodec) {
    this.qbftExtraDataCodec = qbftExtraDataCodec;
  }

  @Override
  public QbftBlock readFrom(final RLPInput rlpInput) {
    Block besuBlock =
        Block.readFrom(rlpInput, BftBlockHeaderFunctions.forCommittedSeal(qbftExtraDataCodec));
    return new QbftBlockAdaptor(besuBlock);
  }

  @Override
  public void writeTo(final QbftBlock block, final RLPOutput rlpOutput) {
    BlockUtil.toBesuBlock(block).writeTo(rlpOutput);
  }
}
