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
package org.hyperledger.besu.ethereum.eth.peervalidation;

import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface PeerValidator {

  /**
   * Whether the peer can currently be validated.
   *
   * @param ethPeer The peer that need validation.
   * @return {@code} True if peer can be validated now.
   */
  boolean canBeValidated(final EthPeer ethPeer);

  /**
   * If the peer cannot currently be validated, returns a timeout indicating how long to wait.
   * before trying to validate the peer again.
   *
   * @param ethPeer The peer to be validated.
   * @return A duration representing how long to wait before trying to validate this peer again.
   */
  Duration nextValidationCheckTimeout(final EthPeer ethPeer);

  /**
   * Validates the given peer.
   *
   * @param ethContext Utilities for working with the eth sub-protocol.
   * @param ethPeer The peer to be validated.
   * @return True if the peer is valid, false otherwise.
   */
  CompletableFuture<Boolean> validatePeer(final EthContext ethContext, final EthPeer ethPeer);

  /**
   * Reason the peer will disconnect when the validator fail.
   *
   * @param ethPeer The peer to be disconnected.
   * @return The reason for disconnecting.
   */
  default DisconnectReason getDisconnectReason(final EthPeer ethPeer) {
    return DisconnectReason.SUBPROTOCOL_TRIGGERED;
  }
}
