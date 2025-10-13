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

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;

import java.util.function.Predicate;

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
  SubProtocol getSubProtocol();

  /**
   * Gets the request data to send to the EthPeer
   *
   * @return the request data to send to the EthPeer
   */
  MessageData getRequestMessage();

  /**
   * Parses and processes the MessageData response from the EthPeer
   *
   * @param messageData the response MessageData to be parsed
   * @return a T built from the response MessageData
   * @throws InvalidPeerTaskResponseException if the response messageData is invalid
   */
  T processResponse(MessageData messageData) throws InvalidPeerTaskResponseException;

  /**
   * Gets the number of times this task may be attempted against other peers
   *
   * @return the number of times this task may be attempted against other peers
   */
  default int getRetriesWithOtherPeer() {
    return 5;
  }

  /**
   * Gets the number of times this task may be attempted against the same peer
   *
   * @return the number of times this task may be attempted against the same peer
   */
  default int getRetriesWithSamePeer() {
    return 5;
  }

  /**
   * Gets a Predicate that checks if an EthPeer is suitable for this PeerTask
   *
   * @return a Predicate that checks if an EthPeer is suitable for this PeerTask
   */
  Predicate<EthPeer> getPeerRequirementFilter();

  /**
   * Performs a high level check of the results, returning a PeerTaskValidationResponse to describe
   * the result of the check
   *
   * @param result The results of the PeerTask, as returned by processResponse
   * @return a PeerTaskValidationResponse to describe the result of the check
   */
  PeerTaskValidationResponse validateResult(T result);

  default void postProcessResult(final PeerTaskExecutorResult<T> result) {}
}
