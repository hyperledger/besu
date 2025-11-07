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
package org.hyperledger.besu.ethereum.eth.manager.task;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.messages.BlockBodiesMessage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;

/** Requests bodies from a peer by header, matches up headers to bodies, and returns blocks. */
public class GetBodiesFromPeerTask extends AbstractGetBodiesFromPeerTask<Block, BlockBody> {

  private GetBodiesFromPeerTask(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    super(protocolSchedule, ethContext, headers, metricsSystem);
  }

  public static GetBodiesFromPeerTask forHeaders(
      final ProtocolSchedule protocolSchedule,
      final EthContext ethContext,
      final List<BlockHeader> headers,
      final MetricsSystem metricsSystem) {
    return new GetBodiesFromPeerTask(protocolSchedule, ethContext, headers, metricsSystem);
  }

  @Override
  Block getBlock(final BlockHeader header, final BlockBody body) {
    return new Block(header, body);
  }

  @Override
  List<BlockBody> getBodies(
      final BlockBodiesMessage message, final ProtocolSchedule protocolSchedule) {
    return message.bodies(protocolSchedule);
  }

  @Override
  boolean bodyMatchesHeader(final BlockBody body, final BlockHeader header) {
    final BodyIdentifier headerBlockId = new BodyIdentifier(header);
    final BodyIdentifier bodyBlockId = new BodyIdentifier(body);
    return headerBlockId.equals(bodyBlockId);
  }
}
