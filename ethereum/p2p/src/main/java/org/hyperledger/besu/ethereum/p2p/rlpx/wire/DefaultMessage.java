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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire;

import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;

/**
 * Simple implementation of {@link Message} that associates a {@link MessageData} instance with a
 * {@link PeerConnection}.
 */
public final class DefaultMessage implements Message {

  private final MessageData data;

  private final PeerConnection connection;

  public DefaultMessage(final PeerConnection channel, final MessageData data) {
    this.connection = channel;
    this.data = data;
  }

  @Override
  public PeerConnection getConnection() {
    return connection;
  }

  @Override
  public MessageData getData() {
    return data;
  }
}
