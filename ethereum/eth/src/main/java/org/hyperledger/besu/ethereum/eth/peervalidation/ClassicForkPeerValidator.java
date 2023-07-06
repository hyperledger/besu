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
package org.hyperledger.besu.ethereum.eth.peervalidation;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassicForkPeerValidator extends AbstractPeerBlockValidator {
  private static final Logger LOG = LoggerFactory.getLogger(ClassicForkPeerValidator.class);

  ClassicForkPeerValidator(
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final long daoBlockNumber,
      final long chainHeightEstimationBuffer) {
    super(protocolSchedule, metricsSystem, daoBlockNumber, chainHeightEstimationBuffer);
  }

  public ClassicForkPeerValidator(
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final long daoBlockNumber) {
    this(protocolSchedule, metricsSystem, daoBlockNumber, DEFAULT_CHAIN_HEIGHT_ESTIMATION_BUFFER);
  }

  @Override
  boolean validateBlockHeader(final EthPeer ethPeer, final BlockHeader header) {
    final boolean validClassicBlock =
        MainnetBlockHeaderValidator.validateHeaderForClassicFork(header);
    if (!validClassicBlock) {
      LOG.info("Peer {} is invalid because Classic block ({}) is invalid.", ethPeer, blockNumber);
    }
    return validClassicBlock;
  }
}
