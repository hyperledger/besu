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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestManager {

  private static final Logger LOG = LoggerFactory.getLogger(RequestManager.class);
  private final AtomicLong requestIdCounter = new AtomicLong(0);
  private final Map<BigInteger, ResponseStream> responseStreams = new ConcurrentHashMap<>();
  private final EthPeer peer;
  private final boolean supportsRequestId;
  private final String protocolName;

  private final AtomicInteger outstandingRequests = new AtomicInteger(0);

  public RequestManager(
      final EthPeer peer, final boolean supportsRequestId, final String protocolName) {
    this.peer = peer;
    this.supportsRequestId = supportsRequestId;
    this.protocolName = protocolName;
  }

  public int outstandingRequests() {
    return outstandingRequests.get();
  }

  public String getProtocolName() {
    return protocolName;
  }

  public ResponseStream dispatchRequest(final RequestSender sender, final MessageData messageData)
      throws PeerNotConnected {
    outstandingRequests.incrementAndGet();
    final BigInteger requestId = BigInteger.valueOf(requestIdCounter.getAndIncrement());
    final ResponseStream stream = createStream(requestId);
    sender.send(supportsRequestId ? messageData.wrapMessageData(requestId) : messageData);
    return stream;
  }

  public void dispatchResponse(final EthMessage ethMessage) {
    final Collection<ResponseStream> streams = List.copyOf(responseStreams.values());
    final int count = outstandingRequests.decrementAndGet();
    try {
      if (supportsRequestId) {
        // If there's a requestId, find the specific stream it belongs to
        final Map.Entry<BigInteger, MessageData> requestIdAndEthMessage =
            ethMessage.getData().unwrapMessageData();
        Optional.ofNullable(responseStreams.get(requestIdAndEthMessage.getKey()))
            .ifPresentOrElse(
                responseStream -> responseStream.processMessage(requestIdAndEthMessage.getValue()),
                // disconnect on incorrect requestIds
                () -> {
                  LOG.debug(
                      "Request ID incorrect (BREACH_OF_PROTOCOL), disconnecting peer {}", peer);
                  peer.disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL);
                });
      } else {
        // otherwise iterate through all of them
        streams.forEach(stream -> stream.processMessage(ethMessage.getData()));
      }
    } catch (final RLPException e) {
      LOG.debug(
          "Received malformed message {} (BREACH_OF_PROTOCOL), disconnecting: {}",
          ethMessage.getData(),
          peer,
          e);

      peer.disconnect(DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL);
    }

    if (count == 0) {
      // No possibility of any remaining outstanding messages
      closeOutstandingStreams(streams);
    }
  }

  public void close() {
    closeOutstandingStreams(responseStreams.values());
  }

  private ResponseStream createStream(final BigInteger requestId) {
    final ResponseStream stream = new ResponseStream(peer, () -> deregisterStream(requestId));
    responseStreams.put(requestId, stream);
    return stream;
  }

  /** Close all current streams. This will be called when the peer disconnects. */
  private void closeOutstandingStreams(final Collection<ResponseStream> outstandingStreams) {
    outstandingStreams.forEach(ResponseStream::close);
  }

  private void deregisterStream(final BigInteger id) {
    responseStreams.remove(id);
  }

  @FunctionalInterface
  public interface RequestSender {
    void send(final MessageData messageData) throws PeerNotConnected;
  }

  @FunctionalInterface
  public interface ResponseCallback {

    /**
     * Process a potential message response
     *
     * @param streamClosed True if the ResponseStream is being shut down and will no longer deliver
     *     messages.
     * @param message the message to be processed
     * @param peer the peer that owns this response stream
     */
    void exec(boolean streamClosed, MessageData message, EthPeer peer);
  }

  @FunctionalInterface
  public interface DeregistrationProcessor {
    void exec();
  }

  private static class Response {
    final boolean closed;
    final MessageData message;

    private Response(final boolean closed, final MessageData message) {
      this.closed = closed;
      this.message = message;
    }
  }

  public static class ResponseStream {
    private final EthPeer peer;
    private final DeregistrationProcessor deregisterCallback;
    private final Queue<Response> bufferedResponses = new ConcurrentLinkedQueue<>();
    private volatile boolean closed = false;
    private volatile ResponseCallback responseCallback = null;

    public ResponseStream(final EthPeer peer, final DeregistrationProcessor deregisterCallback) {
      this.peer = peer;
      this.deregisterCallback = deregisterCallback;
    }

    public ResponseStream then(final ResponseCallback callback) {
      if (responseCallback != null) {
        // For now just manage a single callback for simplicity.  We could expand this to support
        // multiple listeners in the future.
        throw new IllegalStateException("Response streams expect only a single callback");
      }
      responseCallback = callback;
      dispatchBufferedResponses();
      return this;
    }

    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      deregisterCallback.exec();
      bufferedResponses.add(new Response(true, null));
      dispatchBufferedResponses();
    }

    public EthPeer getPeer() {
      return peer;
    }

    private void processMessage(final MessageData message) {
      if (closed) {
        return;
      }
      bufferedResponses.add(new Response(false, message));
      dispatchBufferedResponses();
    }

    private void dispatchBufferedResponses() {
      if (responseCallback == null) {
        return;
      }
      Response response = bufferedResponses.poll();
      while (response != null) {
        responseCallback.exec(response.closed, response.message, peer);
        response = bufferedResponses.poll();
      }
    }
  }
}
