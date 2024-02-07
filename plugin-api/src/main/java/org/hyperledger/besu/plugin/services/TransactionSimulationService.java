/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;

import java.util.Optional;

/** Transaction simulation service interface */
@Unstable
public interface TransactionSimulationService extends BesuService {
  /**
   * Simulate transaction execution at the block identified by the hash
   *
   * @param transaction tx
   * @param blockHash the hash of the block
   * @param operationTracer the tracer
   * @return the result of the simulation
   */
  Optional<TransactionSimulationResult> simulate(
      Transaction transaction, Hash blockHash, OperationTracer operationTracer);
}
