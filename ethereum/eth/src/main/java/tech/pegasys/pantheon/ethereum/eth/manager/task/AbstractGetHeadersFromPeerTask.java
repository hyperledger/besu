/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.manager.task;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Retrieves a sequence of headers from a peer. */
public abstract class AbstractGetHeadersFromPeerTask
    extends AbstractPeerRequestTask<List<BlockHeader>> {

  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<?> protocolSchedule;
  protected final int count;
  protected final int skip;
  protected final boolean reverse;

  protected AbstractGetHeadersFromPeerTask(
      final ProtocolSchedule<?> protocolSchedule,
      final EthContext ethContext,
      final int count,
      final int skip,
      final boolean reverse,
      final MetricsSystem metricsSystem) {
    super(ethContext, EthPV62.GET_BLOCK_HEADERS, metricsSystem);
    checkArgument(count > 0);
    this.protocolSchedule = protocolSchedule;
    this.count = count;
    this.skip = skip;
    this.reverse = reverse;
  }

  @Override
  protected Optional<List<BlockHeader>> processResponse(
      final boolean streamClosed, final MessageData message, final EthPeer peer) {
    if (streamClosed) {
      // All outstanding requests have been responded to and we still haven't found the response
      // we wanted. It must have been empty or contain data that didn't match.
      peer.recordUselessResponse("headers");
      return Optional.of(Collections.emptyList());
    }

    final BlockHeadersMessage headersMessage = BlockHeadersMessage.readFrom(message);
    final List<BlockHeader> headers = headersMessage.getHeaders(protocolSchedule);
    if (headers.isEmpty()) {
      // Message contains no data - nothing to do
      return Optional.empty();
    }
    if (headers.size() > count) {
      // Too many headers - this isn't our response
      return Optional.empty();
    }

    final BlockHeader firstHeader = headers.get(0);
    if (!matchesFirstHeader(firstHeader)) {
      // This isn't our message - nothing to do
      return Optional.empty();
    }

    final List<BlockHeader> headersList = new ArrayList<>();
    headersList.add(firstHeader);
    long prevNumber = firstHeader.getNumber();

    final int expectedDelta = reverse ? -(skip + 1) : (skip + 1);
    for (int i = 1; i < headers.size(); i++) {
      final BlockHeader header = headers.get(i);
      if (header.getNumber() != prevNumber + expectedDelta) {
        // Skip doesn't match, this isn't our data
        return Optional.empty();
      }
      prevNumber = header.getNumber();
      headersList.add(header);
    }

    LOG.debug("Received {} of {} headers requested from peer.", headersList.size(), count);
    return Optional.of(headersList);
  }

  protected abstract boolean matchesFirstHeader(BlockHeader firstHeader);
}
