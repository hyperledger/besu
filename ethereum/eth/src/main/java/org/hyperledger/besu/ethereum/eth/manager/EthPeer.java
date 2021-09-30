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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.messages.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockBodiesMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetBlockHeadersMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetNodeDataMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetPooledTransactionsMessage;
import org.hyperledger.besu.ethereum.eth.messages.GetReceiptsMessage;
import org.hyperledger.besu.ethereum.eth.messages.SnapV1;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class EthPeer {
  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_OUTSTANDING_REQUESTS = 5;

  private final PeerConnection connection;

  private final int maxTrackedSeenBlocks = 300;

  private final Set<Hash> knownBlocks =
      Collections.newSetFromMap(
          Collections.synchronizedMap(
              new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<Hash, Boolean> eldest) {
                  return size() > maxTrackedSeenBlocks;
                }
              }));
  private final String protocolName;
  private final Clock clock;
  private final List<NodeMessagePermissioningProvider> permissioningProviders;
  private final ChainState chainHeadState = new ChainState();
  private final AtomicBoolean statusHasBeenSentToPeer = new AtomicBoolean(false);
  private final AtomicBoolean statusHasBeenReceivedFromPeer = new AtomicBoolean(false);
  private final AtomicBoolean fullyValidated = new AtomicBoolean(false);
  private final AtomicInteger lastProtocolVersion = new AtomicInteger(0);

  private volatile long lastRequestTimestamp = 0;

  private final Map<String, Map<Integer, RequestManager>> requestManagers;
  private final AtomicReference<Consumer<EthPeer>> onStatusesExchanged = new AtomicReference<>();
  private final PeerReputation reputation = new PeerReputation();
  private final Map<PeerValidator, Boolean> validationStatus = new ConcurrentHashMap<>();

  private static final Map<Integer, Integer> roundMessages;

  static {
    roundMessages = new HashMap<>();
    roundMessages.put(EthPV62.BLOCK_HEADERS, EthPV62.GET_BLOCK_HEADERS);
    roundMessages.put(EthPV62.BLOCK_BODIES, EthPV62.GET_BLOCK_BODIES);
    roundMessages.put(EthPV63.RECEIPTS, EthPV63.GET_RECEIPTS);
    roundMessages.put(EthPV63.NODE_DATA, EthPV63.GET_NODE_DATA);
    roundMessages.put(EthPV65.POOLED_TRANSACTIONS, EthPV65.GET_POOLED_TRANSACTIONS);

    roundMessages.put(SnapV1.ACCOUNT_RANGE, SnapV1.GET_ACCOUNT_RANGE);
    roundMessages.put(SnapV1.STORAGE_RANGE, SnapV1.GET_STORAGE_RANGE);
    roundMessages.put(SnapV1.BYTECODES, SnapV1.GET_BYTECODES);
    roundMessages.put(SnapV1.TRIE_NODES, SnapV1.GET_TRIE_NODES);
  }

  @VisibleForTesting
  public EthPeer(
      final PeerConnection connection,
      final String protocolName,
      final Consumer<EthPeer> onStatusesExchanged,
      final List<PeerValidator> peerValidators,
      final Clock clock,
      final List<NodeMessagePermissioningProvider> permissioningProviders) {
    this.connection = connection;
    this.protocolName = protocolName;
    this.clock = clock;
    this.permissioningProviders = permissioningProviders;
    this.onStatusesExchanged.set(onStatusesExchanged);
    peerValidators.forEach(peerValidator -> validationStatus.put(peerValidator, false));
    fullyValidated.set(peerValidators.isEmpty());

    this.requestManagers = new HashMap<>();

    initEthRequestManagers();
    if (connection.capability(SnapProtocol.NAME) != null) {
      initSnapRequestManagers();
    }
  }

  private void initEthRequestManagers() {
    final boolean supportsRequestId =
        getAgreedCapabilities().stream().anyMatch(EthProtocol::isEth66Compatible);
    // eth protocol
    final Map<Integer, RequestManager> ethRequestManagers = new HashMap<>();
    ethRequestManagers.put(
        EthPV62.GET_BLOCK_HEADERS, new RequestManager(this, supportsRequestId, EthProtocol.NAME));
    ethRequestManagers.put(
        EthPV62.GET_BLOCK_BODIES, new RequestManager(this, supportsRequestId, EthProtocol.NAME));
    ethRequestManagers.put(
        EthPV63.GET_RECEIPTS, new RequestManager(this, supportsRequestId, EthProtocol.NAME));
    ethRequestManagers.put(
        EthPV63.GET_NODE_DATA, new RequestManager(this, supportsRequestId, EthProtocol.NAME));
    ethRequestManagers.put(
        EthPV65.GET_POOLED_TRANSACTIONS,
        new RequestManager(this, supportsRequestId, EthProtocol.NAME));
    requestManagers.put(EthProtocol.NAME, ethRequestManagers);
  }

  private void initSnapRequestManagers() {
    // snap protocol
    final Map<Integer, RequestManager> snapRequestManagers = new HashMap<>();
    snapRequestManagers.put(
        SnapV1.GET_ACCOUNT_RANGE, new RequestManager(this, true, SnapProtocol.NAME));
    snapRequestManagers.put(
        SnapV1.GET_STORAGE_RANGE, new RequestManager(this, true, SnapProtocol.NAME));
    requestManagers.put(SnapProtocol.NAME, snapRequestManagers);
  }

  public void markValidated(final PeerValidator validator) {
    if (!validationStatus.containsKey(validator)) {
      throw new IllegalArgumentException("Attempt to update unknown validation status");
    }
    validationStatus.put(validator, true);
    fullyValidated.set(validationStatus.values().stream().allMatch(b -> b));
  }

  /**
   * Check if this peer has been fully validated.
   *
   * @return {@code true} if all peer validation logic has run and successfully validated this peer
   */
  public boolean isFullyValidated() {
    return fullyValidated.get();
  }

  public boolean isDisconnected() {
    return connection.isDisconnected();
  }

  public long addChainEstimatedHeightListener(final ChainState.EstimatedHeightListener listener) {
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
    reputation.recordUselessResponse(System.currentTimeMillis()).ifPresent(this::disconnect);
  }

  public void disconnect(final DisconnectReason reason) {
    connection.disconnect(reason);
  }

  public RequestManager.ResponseStream send(final MessageData messageData) throws PeerNotConnected {
    return send(messageData, this.protocolName);
  }

  public RequestManager.ResponseStream send(
      final MessageData messageData, final String protocolName) throws PeerNotConnected {
    if (connection.getAgreedCapabilities().stream()
        .noneMatch(capability -> capability.getName().equalsIgnoreCase(protocolName))) {
      LOG.debug("Protocol {} unavailable for this peer ", protocolName);
      return null;
    }
    if (permissioningProviders.stream()
        .anyMatch(p -> !p.isMessagePermitted(connection.getRemoteEnode(), messageData.getCode()))) {
      LOG.info(
          "Permissioning blocked sending of message code {} to {}",
          messageData.getCode(),
          connection.getRemoteEnode());
      LOG.debug(
          "Permissioning blocked by providers {}",
          () ->
              permissioningProviders.stream()
                  .filter(
                      p ->
                          !p.isMessagePermitted(
                              connection.getRemoteEnode(), messageData.getCode())));
      return null;
    }

    if (requestManagers.containsKey(protocolName)) {
      final Map<Integer, RequestManager> managers = this.requestManagers.get(protocolName);
      if (managers.containsKey(messageData.getCode())) {
        return sendRequest(managers.get(messageData.getCode()), messageData);
      }
    }

    connection.sendForProtocol(protocolName, messageData);
    return null;
  }

  public RequestManager.ResponseStream getHeadersByHash(
      final Hash hash, final int maxHeaders, final int skip, final boolean reverse)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(hash, maxHeaders, skip, reverse);
    final RequestManager requestManager =
        requestManagers.get(EthProtocol.NAME).get(EthPV62.GET_BLOCK_HEADERS);
    return sendRequest(requestManager, message);
  }

  public RequestManager.ResponseStream getHeadersByNumber(
      final long blockNumber, final int maxHeaders, final int skip, final boolean reverse)
      throws PeerNotConnected {
    final GetBlockHeadersMessage message =
        GetBlockHeadersMessage.create(blockNumber, maxHeaders, skip, reverse);
    return sendRequest(
        requestManagers.get(EthProtocol.NAME).get(EthPV62.GET_BLOCK_HEADERS), message);
  }

  public RequestManager.ResponseStream getBodies(final List<Hash> blockHashes)
      throws PeerNotConnected {
    final GetBlockBodiesMessage message = GetBlockBodiesMessage.create(blockHashes);
    return sendRequest(
        requestManagers.get(EthProtocol.NAME).get(EthPV62.GET_BLOCK_BODIES), message);
  }

  public RequestManager.ResponseStream getReceipts(final List<Hash> blockHashes)
      throws PeerNotConnected {
    final GetReceiptsMessage message = GetReceiptsMessage.create(blockHashes);
    return sendRequest(requestManagers.get(EthProtocol.NAME).get(EthPV63.GET_RECEIPTS), message);
  }

  public RequestManager.ResponseStream getNodeData(final Iterable<Hash> nodeHashes)
      throws PeerNotConnected {
    final GetNodeDataMessage message = GetNodeDataMessage.create(nodeHashes);
    return sendRequest(requestManagers.get(EthProtocol.NAME).get(EthPV63.GET_NODE_DATA), message);
  }

  public RequestManager.ResponseStream getPooledTransactions(final List<Hash> hashes)
      throws PeerNotConnected {
    final GetPooledTransactionsMessage message = GetPooledTransactionsMessage.create(hashes);
    return sendRequest(
        requestManagers.get(EthProtocol.NAME).get(EthPV65.GET_POOLED_TRANSACTIONS), message);
  }

  public RequestManager.ResponseStream getAccountRange(
      final Hash rootHash,
      final Hash startingHash,
      final Hash endingHash,
      final BigInteger responseBytes)
      throws PeerNotConnected {
    final GetAccountRangeMessage message =
        GetAccountRangeMessage.create(rootHash, startingHash, endingHash, responseBytes);
    return sendRequest(
        requestManagers.get(SnapProtocol.NAME).get(SnapV1.GET_ACCOUNT_RANGE), message);
  }

  private RequestManager.ResponseStream sendRequest(
      final RequestManager requestManager, final MessageData messageData) throws PeerNotConnected {
    lastRequestTimestamp = clock.millis();
    return requestManager.dispatchRequest(
        msgData -> connection.sendForProtocol(requestManager.getProtocolName(), msgData),
        messageData);
  }

  public boolean validateReceivedMessage(final EthMessage message, final String protocolName) {
    checkArgument(message.getPeer().equals(this), "Mismatched message sent to peer for dispatch");
    return getRequestManager(protocolName, message.getData().getCode())
        .map(requestManager -> requestManager.outstandingRequests() != 0)
        .orElse(true);
  }

  /**
   * Routes messages originating from this peer to listeners.
   *
   * @param ethMessage the Eth message to dispatch
   * @param protocolName Specific protocol name if needed
   */
  void dispatch(final EthMessage ethMessage, final String protocolName) {
    checkArgument(
        ethMessage.getPeer().equals(this), "Mismatched Eth message sent to peer for dispatch");
    final int messageCode = ethMessage.getData().getCode();
    reputation.resetTimeoutCount(messageCode);

    getRequestManager(protocolName, messageCode)
        .ifPresentOrElse(
            requestManager -> requestManager.dispatchResponse(ethMessage),
            () -> {
              LOG.trace(
                  "Message {} not expected has just been received for {} ",
                  messageCode,
                  protocolName);
            });
  }

  /**
   * Routes messages originating from this peer to listeners.
   *
   * @param ethMessage the Eth message to dispatch
   */
  void dispatch(final EthMessage ethMessage) {
    dispatch(ethMessage, protocolName);
  }

  private Optional<RequestManager> getRequestManager(final String protocolName, final int code) {
    if (requestManagers.containsKey(protocolName)) {
      final Map<Integer, RequestManager> managers = requestManagers.get(protocolName);
      final Integer requestCode = roundMessages.getOrDefault(code, -1);
      if (managers.containsKey(requestCode)) {
        return Optional.of(managers.get(requestCode));
      }
    }
    return Optional.empty();
  }

  public Map<Integer, AtomicInteger> timeoutCounts() {
    return reputation.timeoutCounts();
  }

  void handleDisconnect() {
    requestManagers.forEach(
        (protocolName, map) -> map.forEach((code, requestManager) -> requestManager.close()));
  }

  public void registerKnownBlock(final Hash hash) {
    knownBlocks.add(hash);
  }

  public void registerStatusSent() {
    statusHasBeenSentToPeer.set(true);
    maybeExecuteStatusesExchangedCallback();
  }

  public void registerStatusReceived(
      final Hash hash, final Difficulty td, final int protocolVersion) {
    chainHeadState.statusReceived(hash, td);
    lastProtocolVersion.set(protocolVersion);
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
  boolean statusHasBeenReceived() {
    return statusHasBeenReceivedFromPeer.get();
  }

  /**
   * Return true if we have sent a status message to this peer.
   *
   * @return true if we have sent a status message to this peer.
   */
  boolean statusHasBeenSentToPeer() {
    return statusHasBeenSentToPeer.get();
  }

  public boolean hasSeenBlock(final Hash hash) {
    return knownBlocks.contains(hash);
  }

  /**
   * Return This peer's current chain state.
   *
   * @return This peer's current chain state.
   */
  public ChainState chainState() {
    return chainHeadState;
  }

  public int getLastProtocolVersion() {
    return lastProtocolVersion.get();
  }

  public String getProtocolName() {
    return protocolName;
  }

  /**
   * Return A read-only snapshot of this peer's current {@code chainState} }
   *
   * @return A read-only snapshot of this peer's current {@code chainState} }
   */
  public ChainHeadEstimate chainStateSnapshot() {
    return chainHeadState.getSnapshot();
  }

  public void registerHeight(final Hash blockHash, final long height) {
    chainHeadState.update(blockHash, height);
  }

  public int outstandingRequests() {
    final AtomicInteger count = new AtomicInteger(0);
    requestManagers.forEach(
        (protocolName, map) ->
            map.forEach(
                (code, requestManager) -> count.getAndAdd(requestManager.outstandingRequests())));
    return count.get();
  }

  public long getLastRequestTimestamp() {
    return lastRequestTimestamp;
  }

  public boolean hasAvailableRequestCapacity() {
    return outstandingRequests() < MAX_OUTSTANDING_REQUESTS;
  }

  public Set<Capability> getAgreedCapabilities() {
    return connection.getAgreedCapabilities();
  }

  public PeerConnection getConnection() {
    return connection;
  }

  public Bytes nodeId() {
    return connection.getPeerInfo().getNodeId();
  }

  @Override
  public String toString() {
    return String.format("Peer %s...", nodeId().toString().substring(0, 20));
  }

  @FunctionalInterface
  public interface DisconnectCallback {
    void onDisconnect(EthPeer peer);
  }
}
