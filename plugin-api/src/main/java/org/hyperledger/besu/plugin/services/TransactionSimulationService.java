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
package org.hyperledger.besu.plugin.services;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StateOverrideMap;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;

import java.util.Optional;

/** Transaction simulation service interface */
@Unstable
public interface TransactionSimulationService extends BesuService {

  /**
   * Simulate transaction execution at the block identified by the hash if present, otherwise on the
   * pending block, with optional state overrides that can be applied before the simulation.
   *
   * @param transaction tx
   * @param stateOverrides state overrides to apply to this simulation
   * @param maybeBlockHash optional hash of the block, empty to simulate on pending block
   * @param operationTracer the tracer
   * @param isAllowExceedingBalance should ignore the sender balance during the simulation?
   * @return the result of the simulation
   */
  Optional<TransactionSimulationResult> simulate(
      Transaction transaction,
      Optional<StateOverrideMap> stateOverrides,
      Optional<Hash> maybeBlockHash,
      OperationTracer operationTracer,
      boolean isAllowExceedingBalance);

  /**
   * Simulate transaction execution at the block identified by the hash if present, otherwise on the
   * pending block
   *
   * @param transaction tx
   * @param maybeBlockHash optional hash of the block, empty to simulate on pending block
   * @param operationTracer the tracer
   * @param isAllowExceedingBalance should ignore the sender balance during the simulation?
   * @return the result of the simulation
   */
  default Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Optional<Hash> maybeBlockHash,
      final OperationTracer operationTracer,
      final boolean isAllowExceedingBalance) {
    return simulate(
        transaction, Optional.empty(), maybeBlockHash, operationTracer, isAllowExceedingBalance);
  }

  /**
   * Simulate transaction execution at the block identified by the hash
   *
   * @param transaction tx
   * @param blockHash then hash of the block
   * @param operationTracer the tracer
   * @param isAllowExceedingBalance should ignore the sender balance during the simulation?
   * @return the result of the simulation
   * @deprecated use {@link #simulate(Transaction, Optional, OperationTracer, boolean)}
   */
  @Deprecated(since = "24.12", forRemoval = true)
  default Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Hash blockHash,
      final OperationTracer operationTracer,
      final boolean isAllowExceedingBalance) {
    return simulate(
        transaction,
        Optional.empty(),
        Optional.of(blockHash),
        operationTracer,
        isAllowExceedingBalance);
  }

  /**
   * Simulate transaction execution at the block identified by the hash, with optional state
   * overrides that can be applied before the simulation.
   *
   * @param transaction tx
   * @param stateOverrides state overrides to apply to this simulation
   * @param blockHash the hash of the block
   * @param operationTracer the tracer
   * @param isAllowExceedingBalance should ignore the sender balance during the simulation?
   * @return the result of the simulation
   * @deprecated use {@link #simulate(Transaction, Optional, Optional, OperationTracer, boolean)}
   */
  @Deprecated(since = "24.12", forRemoval = true)
  default Optional<TransactionSimulationResult> simulate(
      final Transaction transaction,
      final Optional<StateOverrideMap> stateOverrides,
      final Hash blockHash,
      final OperationTracer operationTracer,
      final boolean isAllowExceedingBalance) {
    return simulate(
        transaction,
        stateOverrides,
        Optional.of(blockHash),
        operationTracer,
        isAllowExceedingBalance);
  }
}
