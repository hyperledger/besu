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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RequestManager {
  private final AtomicLong responseStreamId = new AtomicLong(0L);
  private final Map<Long, ResponseStream> responseStreams = new ConcurrentHashMap<>();
  private final EthPeer peer;

  private final AtomicInteger outstandingRequests = new AtomicInteger(0);

  public RequestManager(final EthPeer peer) {
    this.peer = peer;
  }

  public int outstandingRequests() {
    return outstandingRequests.get();
  }

  public ResponseStream dispatchRequest(final RequestSender sender) throws PeerNotConnected {
    outstandingRequests.incrementAndGet();
    final ResponseStream stream = createStream();
    sender.send();
    return stream;
  }

  public void dispatchResponse(final EthMessage message) {
    final Collection<ResponseStream> streams = new ArrayList<>(responseStreams.values());
    final int count = outstandingRequests.decrementAndGet();

    streams.forEach(s -> s.processMessage(message.getData()));
    if (count == 0) {
      // No possibility of any remaining outstanding messages
      closeOutstandingStreams(streams);
    }
  }

  public void close() {
    closeOutstandingStreams(responseStreams.values());
  }

  private ResponseStream createStream() {
    final long listenerId = nextStreamId();
    final ResponseStream stream = new ResponseStream(peer, () -> deregisterStream(listenerId));
    responseStreams.put(listenerId, stream);
    return stream;
  }

  /** Close all current streams. This will be called when the peer disconnects. */
  private void closeOutstandingStreams(final Collection<ResponseStream> outstandingStreams) {
    outstandingStreams.forEach(ResponseStream::close);
  }

  private void deregisterStream(final long id) {
    responseStreams.remove(id);
  }

  private long nextStreamId() {
    return responseStreamId.incrementAndGet();
  }

  @FunctionalInterface
  public interface RequestSender {
    void send() throws PeerNotConnected;
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
