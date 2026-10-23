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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.data.TransactionSimulationResult;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService.SimulationParameters;
import org.hyperledger.besu.plugins.graphql.GraphQLContextType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** Executes the {@code call} and {@code estimateGas} fields of blocks and of the pending state. */
final class CallExecutor {

  private static final String BLOCK_NOT_FOUND = "BLOCK_NOT_FOUND";

  private CallExecutor() {}

  /**
   * Simulates the call described by the {@code data} argument on the world state after a block,
   * using that block's header as the execution environment.
   *
   * @param environment the data fetching environment
   * @param blockHash supplies the hash of the block, once the arguments have been read
   * @param simulationParameters the simulation parameters
   * @return the result of the call, empty if the block or its world state is unavailable
   */
  static Optional<CallResult> executeCall(
      final DataFetchingEnvironment environment,
      final Supplier<Hash> blockHash,
      final EnumSet<SimulationParameters> simulationParameters) {
    final Map<String, Object> callData = environment.getArgument("data");
    final Optional<Address> from = Optional.ofNullable((Address) callData.get("from"));
    final Optional<Address> to = Optional.ofNullable((Address) callData.get("to"));
    final var gasParam = callData.get("gas");
    final OptionalLong gas =
        gasParam != null ? OptionalLong.of((Long) gasParam) : OptionalLong.empty();
    // The BigInt scalar yields a String, so these casts fail when a client sets these fields. That
    // is what Besu has always done, and is kept as is.
    final Optional<Wei> gasPrice =
        Optional.ofNullable((UInt256) callData.get("gasPrice")).map(Wei::of);
    final Optional<Wei> value = Optional.ofNullable((UInt256) callData.get("value")).map(Wei::of);
    final Optional<Bytes> data = Optional.ofNullable((Bytes) callData.get("data"));
    final Optional<Wei> maxFeePerGas =
        Optional.ofNullable((UInt256) callData.get("maxFeePerGas")).map(Wei::of);
    final Optional<Wei> maxPriorityFeePerGas =
        Optional.ofNullable((UInt256) callData.get("maxPriorityFeePerGas")).map(Wei::of);

    final TransactionSimulationService transactionSimulationService =
        environment.getGraphQlContext().get(GraphQLContextType.TRANSACTION_SIMULATION_SERVICE);

    final GraphQLCallParameter callParameters =
        new GraphQLCallParameter(
            from, to, gas, gasPrice, value, maxPriorityFeePerGas, maxFeePerGas, data);

    return transactionSimulationService
        .simulate(
            callParameters,
            Optional.empty(),
            blockHash.get(),
            OperationTracer.NO_TRACING,
            simulationParameters)
        .filter(result -> !isBlockNotFound(result))
        .map(
            result -> {
              long status = 0;
              if (result.isSuccessful()) {
                status = 1;
              }
              return new CallResult(status, result.getGasEstimate(), result.result().getOutput());
            });
  }

  /**
   * Whether the simulation could not run because the block is unknown, as for an ommer that was
   * never imported as a block. Besu has always reported that like a block whose world state is
   * unavailable, with no result; such a result also has no transaction to compute the gas from.
   *
   * @param result the simulation result
   * @return true if the block of the simulation was not found
   */
  private static boolean isBlockNotFound(final TransactionSimulationResult result) {
    return result.transaction() == null
        || (result.isInvalid()
            && result.getInvalidReason().filter(BLOCK_NOT_FOUND::equals).isPresent());
  }
}
