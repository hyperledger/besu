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

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftExtraDataProvider;

/**
 * Adaptor class to allow a {@link BftExtraDataCodec} to be used as a {@link QbftExtraDataProvider}.
 */
public class QbftExtraDataProviderAdaptor implements QbftExtraDataProvider {
  private final BftExtraDataCodec bftExtraDataCodec;

  /**
   * Constructs a new QbftExtraDataProvider
   *
   * @param bftExtraDataCodec the bftExtraDataCodec used to decode the extra data from the header
   */
  public QbftExtraDataProviderAdaptor(final BftExtraDataCodec bftExtraDataCodec) {
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  @Override
  public BftExtraData getExtraData(final QbftBlockHeader header) {
    return bftExtraDataCodec.decode(BlockUtil.toBesuBlockHeader(header));
  }
}
