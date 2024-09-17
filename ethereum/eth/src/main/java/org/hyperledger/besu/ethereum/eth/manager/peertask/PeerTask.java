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
package org.hyperledger.besu.ethereum.eth.manager.peertask;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;

/**
 * Represents a task to be executed on an EthPeer by the PeerTaskExecutor
 *
 * @param <T> The type of the result of this PeerTask
 */
public interface PeerTask<T> {
  /**
   * Returns the SubProtocol used for this PeerTask
   *
   * @return the SubProtocol used for this PeerTask
   */
  String getSubProtocol();

  /**
   * Gets the minimum required block number for a peer to have to successfully execute this task
   *
   * @return the minimum required block number for a peer to have to successfully execute this task
   */
  long getRequiredBlockNumber();

  /**
   * Gets the request data to send to the EthPeer
   *
   * @return the request data to send to the EthPeer
   */
  MessageData getRequestMessage();

  /**
   * Parses the MessageData response from the EthPeer
   *
   * @param messageData the response MessageData to be parsed
   * @return a T built from the response MessageData
   * @throws InvalidPeerTaskResponseException if the response messageData is invalid
   */
  T parseResponse(MessageData messageData) throws InvalidPeerTaskResponseException;

  /**
   * Gets the Collection of behaviors this task is expected to exhibit in the PeetTaskExecutor
   *
   * @return the Collection of behaviors this task is expected to exhibit in the PeetTaskExecutor
   */
  Collection<PeerTaskBehavior> getPeerTaskBehaviors();
}
