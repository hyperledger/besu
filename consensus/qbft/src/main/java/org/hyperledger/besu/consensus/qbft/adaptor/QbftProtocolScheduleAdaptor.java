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

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSpec;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

/**
 * Adaptor class to allow a {@link ProtocolSchedule} to be used as a {@link QbftProtocolSchedule}.
 */
public class QbftProtocolScheduleAdaptor implements QbftProtocolSchedule {

  private final ProtocolSchedule besuProtocolSchedule;
  private final ProtocolContext context;

  /**
   * Constructs a new Qbft protocol schedule.
   *
   * @param besuProtocolSchedule The Besu protocol schedule.
   * @param context The protocol context.
   */
  public QbftProtocolScheduleAdaptor(
      final ProtocolSchedule besuProtocolSchedule, final ProtocolContext context) {
    this.besuProtocolSchedule = besuProtocolSchedule;
    this.context = context;
  }

  @Override
  public QbftProtocolSpec getByBlockHeader(final QbftBlockHeader header) {
    final ProtocolSpec protocolSpec =
        besuProtocolSchedule.getByBlockHeader(BlockUtil.toBesuBlockHeader(header));
    return new QbftProtocolSpecAdaptor(protocolSpec, context);
  }
}
