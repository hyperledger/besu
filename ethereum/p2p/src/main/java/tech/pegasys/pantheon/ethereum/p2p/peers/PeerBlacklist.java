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
package tech.pegasys.pantheon.ethereum.p2p.peers;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * A list of nodes that the running client will not communicate with. This can be because of network
 * issues, protocol issues, or by being explicitly set on the command line.
 *
 * <p>Peers are stored and identified strictly by their nodeId, the convenience methods taking
 * {@link Peer}s and {@link PeerConnection}s redirect to the methods that take {@link BytesValue}
 * object that represent the node ID of the banned nodes.
 *
 * <p>The storage list is not infinite. A default cap of 500 is applied and nodes are removed on a
 * first added first removed basis. Adding a new copy of the same node will not affect the priority
 * for removal. The exception to this is a list of banned nodes passed in by reference to the
 * constructor. This list neither adds nor removes from that list passed in.
 */
public class PeerBlacklist implements DisconnectCallback {
  private static final int DEFAULT_BLACKLIST_CAP = 500;

  private static final Set<DisconnectReason> locallyTriggeredBlacklistReasons =
      ImmutableSet.of(
          DisconnectReason.BREACH_OF_PROTOCOL, DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION);

  private static final Set<DisconnectReason> remotelyTriggeredBlacklistReasons =
      ImmutableSet.of(DisconnectReason.INCOMPATIBLE_P2P_PROTOCOL_VERSION);

  private final int blacklistCap;
  private final Set<BytesValue> blacklistedNodeIds =
      Collections.synchronizedSet(
          Collections.newSetFromMap(
              new LinkedHashMap<BytesValue, Boolean>(20, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<BytesValue, Boolean> eldest) {
                  return size() > blacklistCap;
                }
              }));

  /** These nodes are always banned for the life of this list. They are not subject to rollover. */
  private final Set<BytesValue> bannedNodeIds;

  public PeerBlacklist(final int blacklistCap, final Set<BytesValue> bannedNodeIds) {
    this.blacklistCap = blacklistCap;
    this.bannedNodeIds = bannedNodeIds;
  }

  public PeerBlacklist(final int blacklistCap) {
    this(blacklistCap, Collections.emptySet());
  }

  public PeerBlacklist(final Set<BytesValue> bannedNodeIds) {
    this(DEFAULT_BLACKLIST_CAP, bannedNodeIds);
  }

  public PeerBlacklist() {
    this(DEFAULT_BLACKLIST_CAP, Collections.emptySet());
  }

  private boolean contains(final BytesValue nodeId) {
    return blacklistedNodeIds.contains(nodeId) || bannedNodeIds.contains(nodeId);
  }

  public boolean contains(final PeerConnection peer) {
    return contains(peer.getPeerInfo().getNodeId());
  }

  public boolean contains(final Peer peer) {
    return contains(peer.getId());
  }

  public void add(final Peer peer) {
    add(peer.getId());
  }

  public void add(final BytesValue peerId) {
    blacklistedNodeIds.add(peerId);
  }

  @Override
  public void onDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    if (shouldBlacklistForDisconnect(reason, initiatedByPeer)) {
      add(connection.getPeerInfo().getNodeId());
    }
  }

  private boolean shouldBlacklistForDisconnect(
      final DisconnectMessage.DisconnectReason reason, final boolean initiatedByPeer) {
    return (!initiatedByPeer && locallyTriggeredBlacklistReasons.contains(reason))
        || (initiatedByPeer && remotelyTriggeredBlacklistReasons.contains(reason));
  }
}
