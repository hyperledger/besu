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
package org.hyperledger.besu.ethereum.blockcreation.stratum;

import org.hyperledger.besu.ethereum.mainnet.EthHashSolverInputs;

import java.util.function.Function;

public interface StratumProtocol {

  boolean register(byte[] initialMessage, StratumConnection conn);

  void onClose(StratumConnection conn);

  void handle(StratumConnection conn, byte[] message);

  void solveFor(EthHashSolverInputs input);

  void setSubmitCallback(Function<Long, Boolean> submitSolutionCallback);
}
