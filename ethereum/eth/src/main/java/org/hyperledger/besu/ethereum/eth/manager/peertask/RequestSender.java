/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.RequestManager.ResponseStream;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestSender {
  private static final Logger LOG = LoggerFactory.getLogger(RequestSender.class);
  private static final long DEFAULT_TIMEOUT_MS = 20_000;

  private final long timeoutMs;

  public RequestSender() {
    this.timeoutMs = DEFAULT_TIMEOUT_MS;
  }

  public RequestSender(final long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public MessageData sendRequest(
      final String subProtocol, final MessageData requestMessageData, final EthPeer ethPeer)
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException {
    LOG.info("Sending request to " + ethPeer.getLoggableId());
    ResponseStream responseStream =
        ethPeer.send(requestMessageData, subProtocol, ethPeer.getConnection());
    final CompletableFuture<MessageData> responseMessageDataFuture = new CompletableFuture<>();
    responseStream.then(
        (boolean streamClosed, MessageData message, EthPeer peer) -> {
          LOG.info("Completing responseMessageDataFuture from " + peer.getLoggableId());
          responseMessageDataFuture.complete(message);
        });
    LOG.info("Waiting for responseMessageDataFuture to complete from " + ethPeer.getLoggableId());
    return responseMessageDataFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
  }
}
