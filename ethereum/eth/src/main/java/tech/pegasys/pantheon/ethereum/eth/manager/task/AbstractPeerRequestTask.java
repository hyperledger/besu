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

import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.PeerRequest;
import tech.pegasys.pantheon.ethereum.eth.manager.PendingPeerRequest;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.PeerBreachedProtocolException;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public abstract class AbstractPeerRequestTask<R> extends AbstractPeerTask<R> {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  private Duration timeout = DEFAULT_TIMEOUT;
  private final int requestCode;
  private volatile PendingPeerRequest responseStream;

  protected AbstractPeerRequestTask(
      final EthContext ethContext, final int requestCode, final MetricsSystem metricsSystem) {
    super(ethContext, metricsSystem);
    this.requestCode = requestCode;
  }

  public AbstractPeerRequestTask<R> setTimeout(final Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  protected final void executeTask() {
    final CompletableFuture<R> promise = new CompletableFuture<>();
    responseStream = sendRequest();
    responseStream.then(
        stream -> {
          // Start the timeout now that the request has actually been sent
          ethContext.getScheduler().failAfterTimeout(promise, timeout);

          stream.then(
              (streamClosed, message, peer1) ->
                  handleMessage(promise, streamClosed, message, peer1));
        },
        promise::completeExceptionally);

    promise.whenComplete(
        (r, t) -> {
          final Optional<ResponseStream> responseStream = this.responseStream.abort();
          if (t != null) {
            t = ExceptionUtils.rootCause(t);
            if (t instanceof TimeoutException && responseStream.isPresent()) {
              responseStream.get().getPeer().recordRequestTimeout(requestCode);
            }
            result.get().completeExceptionally(t);
          } else if (r != null) {
            // If we got a response we must have had a response stream...
            result.get().complete(new PeerTaskResult<>(responseStream.get().getPeer(), r));
          }
        });
  }

  public PendingPeerRequest sendRequestToPeer(
      final PeerRequest request, final long minimumBlockNumber) {
    return ethContext.getEthPeers().executePeerRequest(request, minimumBlockNumber, assignedPeer);
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
    responseStream.abort().ifPresent(ResponseStream::close);
  }

  protected abstract PendingPeerRequest sendRequest();

  protected abstract Optional<R> processResponse(
      boolean streamClosed, MessageData message, EthPeer peer);
}
