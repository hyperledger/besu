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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState.EstimatedHeightListener;
import tech.pegasys.pantheon.ethereum.eth.manager.RequestManager.ResponseStream;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.eth.messages.GetBlockBodiesMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.GetBlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.GetNodeDataMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.GetReceiptsMessage;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthPeer {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_OUTSTANDING_REQUESTS = 5;

  private final PeerConnection connection;

  private final int maxTrackedSeenBlocks = 300;

  private final Set<Hash> knownBlocks;
  private final String protocolName;
  private final Clock clock;
  private final ChainState chainHeadState;
  private final AtomicBoolean statusHasBeenSentToPeer = new AtomicBoolean(false);
  private final AtomicBoolean statusHasBeenReceivedFromPeer = new AtomicBoolean(false);

  private volatile long lastRequestTimestamp = 0;
  private final RequestManager headersRequestManager = new RequestManager(this);
  private final RequestManager bodiesRequestManager = new RequestManager(this);
  private final RequestManager receiptsRequestManager = new RequestManager(this);
  private final RequestManager nodeDataRequestManager = new RequestManager(this);

  private final AtomicReference<Consumer<EthPeer>> onStatusesExchanged = new AtomicReference<>();
  private final PeerReputation reputation = new PeerReputation();

  EthPeer(
      final PeerConnection connection,
      final String protocolName,
      final Consumer<EthPeer> onStatusesExchanged,
      final Clock clock) {
    this.connection = connection;
    this.protocolName = protocolName;
    this.clock = clock;
    knownBlocks =
        Collections.newSetFromMap(
            Collections.synchronizedMap(
                new LinkedHashMap<Hash, Boolean>(16, 0.75f, true) {
                  @Override
                  protected boolean removeEldestEntry(final Map.Entry<Hash, Boolean> eldest) {
                    return size() > maxTrackedSeenBlocks;
                  }
                }));
    this.chainHeadState = new ChainState();
    this.onStatusesExchanged.set(onStatusesExchanged);
  }

  public boolean isDisconnected() {
    return connection.isDisconnected();
  }

  public long addChainEstimatedHeightListener(final EstimatedHeightListener listener) {
    return chainHeadState.addEstimatedHeightListener(listener);
  }

  public void removeChainEstimatedHeightListener(final long listenerId) {
    chainHeadState.removeEstimatedHeightListener(listenerId);
  }

  public void recordRequestTimeout(final int requestCode) {
    LOG.debug("Timed out while waiting for response from peer {}", this);
    reputation.recordRequestTimeout(requestCode).ifPresent(this::disconnect);
  }

  public void recordUselessResponse(final String requestType) {
    LOG.debug("Received useless response for {} from peer {}", requestType, this);
    reputation.recordUselessResponse(clock.millis()).ifPresent(this::disconnect);
  }

  public void disconnect(final DisconnectReason reason) {
    connection.disconnect(reason);
  }

  public ResponseStream send(final MessageData messageData) throws PeerNotConnected {
    switch (messageData.getCode()) {
      case EthPV62.GET_BLOCK_HEADERS:
        return sendRequest(headersRequestManager, messageData);
      case EthPV62.GET_BLOCK_BODIES:
        return sendRequest(bodiesRequestManager, messageData);
      case EthPV63.GET_RECEIPTS:
        return sendRequest(receiptsRequestManager, messageData);
      case EthPV63.GET_NODE_DATA:
        return sendRequest(nodeDataRequestManager, messageData);
      default:
        connection.sendForProtocol(protocolName, messageData);
        return null;
    }
  }

  public ResponseStream getHeadersByHash(
      final Hash hash, final int maxHeaders, final int skip, final boolean reverse)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(hash, maxHeaders, skip, reverse);
    return sendRequest(headersRequestManager, message);
  }

  public ResponseStream getHeadersByNumber(
      final long blockNumber, final int maxHeaders, final int skip, final boolean reverse)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(blockNumber, maxHeaders, skip, reverse);
    return sendRequest(headersRequestManager, message);
  }

  private ResponseStream sendRequest(
      final RequestManager requestManager, final MessageData messageData) throws PeerNotConnected {
    lastRequestTimestamp = clock.millis();
    return requestManager.dispatchRequest(
        () -> connection.sendForProtocol(protocolName, messageData));
  }

  public ResponseStream getBodies(final List<Hash> blockHashes) throws PeerNotConnected {
    final GetBlockBodiesMessage message = GetBlockBodiesMessage.create(blockHashes);
    return sendRequest(bodiesRequestManager, message);
  }

  public ResponseStream getReceipts(final List<Hash> blockHashes) throws PeerNotConnected {
    final GetReceiptsMessage message = GetReceiptsMessage.create(blockHashes);
    return sendRequest(receiptsRequestManager, message);
  }

  public ResponseStream getNodeData(final Iterable<Hash> nodeHashes) throws PeerNotConnected {
    final GetNodeDataMessage message = GetNodeDataMessage.create(nodeHashes);
    return sendRequest(nodeDataRequestManager, message);
  }

  boolean validateReceivedMessage(final EthMessage message) {
    checkArgument(message.getPeer().equals(this), "Mismatched message sent to peer for dispatch");
    switch (message.getData().getCode()) {
      case EthPV62.BLOCK_HEADERS:
        if (headersRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited headers received.");
          return false;
        }
        break;
      case EthPV62.BLOCK_BODIES:
        if (bodiesRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited bodies received.");
          return false;
        }
        break;
      case EthPV63.RECEIPTS:
        if (receiptsRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited receipts received.");
          return false;
        }
        break;
      case EthPV63.NODE_DATA:
        if (nodeDataRequestManager.outstandingRequests() == 0) {
          LOG.warn("Unsolicited node data received.");
          return false;
        }
        break;
      default:
        // Nothing to do
    }
    return true;
  }

  /**
   * Routes messages originating from this peer to listeners.
   *
   * @param message the message to dispatch
   */
  void dispatch(final EthMessage message) {
    checkArgument(message.getPeer().equals(this), "Mismatched message sent to peer for dispatch");
    switch (message.getData().getCode()) {
      case EthPV62.BLOCK_HEADERS:
        reputation.resetTimeoutCount(EthPV62.GET_BLOCK_HEADERS);
        headersRequestManager.dispatchResponse(message);
        break;
      case EthPV62.BLOCK_BODIES:
        reputation.resetTimeoutCount(EthPV62.GET_BLOCK_BODIES);
        bodiesRequestManager.dispatchResponse(message);
        break;
      case EthPV63.RECEIPTS:
        reputation.resetTimeoutCount(EthPV63.GET_RECEIPTS);
        receiptsRequestManager.dispatchResponse(message);
        break;
      case EthPV63.NODE_DATA:
        reputation.resetTimeoutCount(EthPV63.GET_NODE_DATA);
        nodeDataRequestManager.dispatchResponse(message);
        break;
      default:
        // Nothing to do
    }
  }

  public Map<Integer, AtomicInteger> timeoutCounts() {
    return reputation.timeoutCounts();
  }

  void handleDisconnect() {
    headersRequestManager.close();
    bodiesRequestManager.close();
    receiptsRequestManager.close();
    nodeDataRequestManager.close();
  }

  public void registerKnownBlock(final Hash hash) {
    knownBlocks.add(hash);
  }

  public void registerStatusSent() {
    statusHasBeenSentToPeer.set(true);
    maybeExecuteStatusesExchangedCallback();
  }

  public void registerStatusReceived(final Hash hash, final UInt256 td) {
    chainHeadState.statusReceived(hash, td);
    statusHasBeenReceivedFromPeer.set(true);
    maybeExecuteStatusesExchangedCallback();
  }

  private void maybeExecuteStatusesExchangedCallback() {
    if (readyForRequests()) {
      final Consumer<EthPeer> callback = onStatusesExchanged.getAndSet(null);
      if (callback == null) {
        return;
      }
      callback.accept(this);
    }
  }

  /**
   * Wait until status has been received and verified before using a peer.
   *
   * @return true if the peer is ready to accept requests for data.
   */
  public boolean readyForRequests() {
    return statusHasBeenSentToPeer.get() && statusHasBeenReceivedFromPeer.get();
  }

  /**
   * True if the peer has sent its initial status message to us.
   *
   * @return true if the peer has sent its initial status message to us.
   */
  public boolean statusHasBeenReceived() {
    return statusHasBeenReceivedFromPeer.get();
  }

  /** @return true if we have sent a status message to this peer. */
  public boolean statusHasBeenSentToPeer() {
    return statusHasBeenSentToPeer.get();
  }

  public boolean hasSeenBlock(final Hash hash) {
    return knownBlocks.contains(hash);
  }

  public ChainState chainState() {
    return chainHeadState;
  }

  public void registerHeight(final Hash blockHash, final long height) {
    chainHeadState.update(blockHash, height);
  }

  public int outstandingRequests() {
    return headersRequestManager.outstandingRequests()
        + bodiesRequestManager.outstandingRequests()
        + receiptsRequestManager.outstandingRequests()
        + nodeDataRequestManager.outstandingRequests();
  }

  public long getLastRequestTimestamp() {
    return lastRequestTimestamp;
  }

  public boolean hasAvailableRequestCapacity() {
    return outstandingRequests() < MAX_OUTSTANDING_REQUESTS;
  }

  public BytesValue nodeId() {
    return connection.getPeerInfo().getNodeId();
  }

  @Override
  public String toString() {
    return nodeId().toString().substring(0, 20) + "...";
  }

  @FunctionalInterface
  public interface DisconnectCallback {
    void onDisconnect(EthPeer peer);
  }
}
