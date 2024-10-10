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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PeerTaskRequestSender {
  private static final long DEFAULT_TIMEOUT_MS = 5_000;

  private final long timeoutMs;

  public PeerTaskRequestSender() {
    this.timeoutMs = DEFAULT_TIMEOUT_MS;
  }

  public PeerTaskRequestSender(final long timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public MessageData sendRequest(
      final SubProtocol subProtocol, final MessageData requestMessageData, final EthPeer ethPeer)
      throws PeerConnection.PeerNotConnected,
          ExecutionException,
          InterruptedException,
          TimeoutException {
    ResponseStream responseStream =
        ethPeer.send(requestMessageData, subProtocol.getName(), ethPeer.getConnection());
    final CompletableFuture<MessageData> responseMessageDataFuture = new CompletableFuture<>();
    responseStream.then(
        (boolean streamClosed, MessageData message, EthPeer peer) -> {
          responseMessageDataFuture.complete(message);
        });
    return responseMessageDataFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
  }
}
