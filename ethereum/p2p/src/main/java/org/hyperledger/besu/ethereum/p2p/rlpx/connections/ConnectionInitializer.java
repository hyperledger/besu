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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.ConnectCallback;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

public interface ConnectionInitializer {

  /**
   * Start the connection initializer. Begins listening for incoming connections. Start allowing
   * outbound connections.
   *
   * @return The address on which we're listening for incoming connections.
   */
  CompletableFuture<InetSocketAddress> start();

  /**
   * Shutdown the connection initializer. Stop listening for incoming connections and stop
   * initiating outgoing connections.
   *
   * @return A future that completes when shutdown is complete.
   */
  CompletableFuture<Void> stop();

  /**
   * Listen for newly established inbound connections.
   *
   * @param callback The callback to invoke when a new inbound connection is established.
   */
  void subscribeIncomingConnect(final ConnectCallback callback);

  /**
   * Initiate an outbound connection.
   *
   * @param peer The peer to which we want to establish a connection.
   * @return A future that completes when the connection is established.
   */
  CompletableFuture<PeerConnection> connect(Peer peer);
}
