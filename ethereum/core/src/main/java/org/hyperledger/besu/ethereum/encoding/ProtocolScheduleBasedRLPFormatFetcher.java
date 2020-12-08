/*
 *
 *  * Copyright ConsenSys AG.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.encoding;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Supplier;

public interface ProtocolScheduleBasedRLPFormatFetcher extends Supplier<RLPFormat> {

  static ProtocolScheduleBasedRLPFormatFetcher getByBlockNumber(
      final ProtocolSchedule protocolSchedule, final long blockNumber) {
    return () -> protocolSchedule.getByBlockNumber(blockNumber).getRLPFormat();
  }

  static ProtocolScheduleBasedRLPFormatFetcher getForChainHead(
      final ProtocolSchedule protocolSchedule, final Blockchain blockchain) {
    return () ->
        protocolSchedule.getByBlockNumber(blockchain.getChainHeadBlockNumber()).getRLPFormat();
  }

  static ProtocolScheduleBasedRLPFormatFetcher getAscendingByBlockNumber(
      final ProtocolSchedule protocolSchedule, final long startingBlockNumber) {
    return new ProtocolScheduleBasedRLPFormatFetcher() {
      long currentBlockNumber = startingBlockNumber;

      @Override
      public RLPFormat get() {
        return getByBlockNumber(protocolSchedule, currentBlockNumber++).get();
      }
    };
  }
}
