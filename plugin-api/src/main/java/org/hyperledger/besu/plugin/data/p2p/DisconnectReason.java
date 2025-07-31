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
package org.hyperledger.besu.plugin.data.p2p;

/** P2P disconnect reasons for plugin use. */
public enum DisconnectReason {
  /** Client quitting. */
  CLIENT_QUITTING,
  /** Network idle. */
  NETWORK_IDLE,
  /** Protocol breach. */
  PROTOCOL_BREACH,
  /** Bad protocol. */
  BAD_PROTOCOL,
  /** Useless peer. */
  USELESS_PEER,
  /** Too many peers. */
  TOO_MANY_PEERS,
  /** Already connected. */
  ALREADY_CONNECTED,
  /** Incompatible devp2p version. */
  INCOMPATIBLE_DEVP2P_VERSION,
  /** Null node identity received. */
  NULL_NODE_IDENTITY_RECEIVED,
  /** Client shutting down. */
  CLIENT_SHUTTING_DOWN,
  /** Handshake timeout. */
  HANDSHAKE_TIMEOUT,
  /** Subprotocol reason. */
  SUBPROTOCOL_REASON,
  /** Requested. */
  REQUESTED,
  /** Peer disconnection. */
  PEER_DISCONNECTED,
  /** Unreachable. */
  UNREACHABLE,
  /** Too many requests. */
  TOO_MANY_REQUESTS,
  /** Unknown. */
  UNKNOWN
}
