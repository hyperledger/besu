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
package tech.pegasys.pantheon.ethereum.eth.manager;

import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.PeerBreachedProtocolException;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public abstract class AbstractPeerRequestTask<R> extends AbstractPeerTask<R> {

  private final int requestCode;
  private volatile ResponseStream responseStream;

  protected AbstractPeerRequestTask(final EthContext ethContext, final int requestCode) {
    super(ethContext);
    this.requestCode = requestCode;
  }

  @Override
  protected final void executeTaskWithPeer(final EthPeer peer) throws PeerNotConnected {
    final CompletableFuture<R> promise = new CompletableFuture<>();
    responseStream =
        sendRequest(peer)
            .then(
                (streamClosed, message, peer1) ->
                    handleMessage(promise, streamClosed, message, peer1));

    promise.whenComplete(
        (r, t) -> {
          if (t != null) {
            t = ExceptionUtils.rootCause(t);
            if (t instanceof TimeoutException) {
              peer.recordRequestTimeout(requestCode);
            }
            result.get().completeExceptionally(t);
          } else if (r != null) {
            result.get().complete(new PeerTaskResult<>(peer, r));
          }
        });

    ethContext.getScheduler().failAfterTimeout(promise);
  }

  private void handleMessage(
      final CompletableFuture<R> promise,
      final boolean streamClosed,
      final MessageData message,
      final EthPeer peer) {
    if (promise.isDone()) {
      // We've already got our response, don't pass on the stream closed event.
      return;
    }
    try {
      final Optional<R> result = processResponse(streamClosed, message, peer);
      result.ifPresent(promise::complete);
    } catch (final RLPException e) {
      // Peer sent us malformed data - disconnect
      peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      promise.completeExceptionally(new PeerBreachedProtocolException());
    }
  }

  @Override
  protected void cleanup() {
    super.cleanup();
    final ResponseStream stream = responseStream;
    if (stream != null) {
      stream.close();
    }
  }

  protected abstract ResponseStream sendRequest(EthPeer peer) throws PeerNotConnected;

  protected abstract Optional<R> processResponse(
      boolean streamClosed, MessageData message, EthPeer peer);
}
