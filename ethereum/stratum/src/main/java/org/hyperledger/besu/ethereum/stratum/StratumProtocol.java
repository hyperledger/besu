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
package org.hyperledger.besu.ethereum.stratum;

import org.hyperledger.besu.ethereum.mainnet.EthHashSolution;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;

import java.util.function.Function;

/**
 * Stratum protocol handler.
 *
 * <p>Manages the lifecycle of a TCP connection according to a particular variant of the Stratum
 * protocol.
 */
public interface StratumProtocol {

  /**
   * Checks if the protocol can handle a TCP connection, based on the initial message.
   *
   * @param initialMessage the initial message sent over the TCP connection.
   * @param conn the connection itself
   * @return true if the protocol can handle this connection
   */
  boolean canHandle(String initialMessage, StratumConnection conn);

  /**
   * Callback when a stratum connection is closed.
   *
   * @param conn the connection that just closed
   */
  void onClose(StratumConnection conn);

  /**
   * Handle a message over an established Stratum connection
   *
   * @param conn the Stratum connection
   * @param message the message to handle
   */
  void handle(StratumConnection conn, String message);

  /**
   * Sets the current proof-of-work job.
   *
   * @param input the new proof-of-work job to send to miners
   */
  void setCurrentWorkTask(EthHashSolverInputs input);

  void setSubmitCallback(Function<EthHashSolution, Boolean> submitSolutionCallback);
}
