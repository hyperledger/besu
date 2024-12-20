/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;

import java.util.Optional;

public enum PeerTaskValidationResponse {
  NO_RESULTS_RETURNED(null),
  TOO_MANY_RESULTS_RETURNED(null),
  RESULTS_DO_NOT_MATCH_QUERY(null),
  NON_SEQUENTIAL_HEADERS_RETURNED(
      DisconnectMessage.DisconnectReason.BREACH_OF_PROTOCOL_NON_SEQUENTIAL_HEADERS),
  RESULTS_VALID_AND_GOOD(null);
  private final Optional<DisconnectMessage.DisconnectReason> disconnectReason;

  PeerTaskValidationResponse(final DisconnectMessage.DisconnectReason disconnectReason) {
    this.disconnectReason = Optional.ofNullable(disconnectReason);
  }

  public Optional<DisconnectMessage.DisconnectReason> getDisconnectReason() {
    return disconnectReason;
  }
}
