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

  public PeerBlacklist(final int blacklistCap) {
    this.blacklistCap = blacklistCap;
  }

  public PeerBlacklist() {
    this(DEFAULT_BLACKLIST_CAP);
  }

  private boolean contains(final BytesValue nodeId) {
    return blacklistedNodeIds.contains(nodeId);
  }

  public boolean contains(final PeerConnection peer) {
    return contains(peer.getPeer().getNodeId());
  }

  public boolean contains(final Peer peer) {
    return contains(peer.getId());
  }

  public void add(final Peer peer) {
    add(peer.getId());
  }

  private void add(final BytesValue peerId) {
    blacklistedNodeIds.add(peerId);
  }

  @Override
  public void onDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    if (shouldBlacklistForDisconnect(reason, initiatedByPeer)) {
      add(connection.getPeer().getNodeId());
    }
  }

  private boolean shouldBlacklistForDisconnect(
      final DisconnectMessage.DisconnectReason reason, final boolean initiatedByPeer) {
    return (!initiatedByPeer && locallyTriggeredBlacklistReasons.contains(reason))
        || (initiatedByPeer && remotelyTriggeredBlacklistReasons.contains(reason));
  }
}
