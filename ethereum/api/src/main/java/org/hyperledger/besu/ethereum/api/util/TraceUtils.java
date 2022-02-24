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
package org.hyperledger.besu.ethereum.api.util;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType.VM_TRACE;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TraceCallResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff.StateDiffGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.diff.StateDiffTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.MixInIgnoreRevertReason;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm.VmTraceGenerator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TraceUtils {
  public static JsonNode getTraceCallResult(
      final ProtocolSchedule protocolSchedule,
      final Set<TraceType> traceTypes,
      final Optional<TransactionSimulatorResult> maybeSimulatorResult,
      final TransactionTrace transactionTrace,
      final Block block) {
    final TraceCallResult.Builder builder = TraceCallResult.builder();
    final ObjectMapper MAPPER_IGNORE_REVERT_REASON = new ObjectMapper();
    // The trace_call specification does not output the revert reason, so we have to remove it
    MAPPER_IGNORE_REVERT_REASON.addMixIn(FlatTrace.class, MixInIgnoreRevertReason.class);

    transactionTrace
        .getResult()
        .getRevertReason()
        .ifPresentOrElse(
            revertReason -> builder.output(revertReason.toHexString()),
            () -> builder.output(maybeSimulatorResult.get().getOutput().toString()));

    if (traceTypes.contains(TraceType.STATE_DIFF)) {
      new StateDiffGenerator()
          .generateStateDiff(transactionTrace)
          .forEachOrdered(stateDiff -> builder.stateDiff((StateDiffTrace) stateDiff));
    }

    if (traceTypes.contains(TraceType.TRACE)) {
      FlatTraceGenerator.generateFromTransactionTrace(
              protocolSchedule, transactionTrace, block, new AtomicInteger(), false)
          .forEachOrdered(trace -> builder.addTrace((FlatTrace) trace));
    }

    if (traceTypes.contains(VM_TRACE)) {
      new VmTraceGenerator(transactionTrace)
          .generateTraceStream()
          .forEachOrdered(vmTrace -> builder.vmTrace((VmTrace) vmTrace));
    }

    return MAPPER_IGNORE_REVERT_REASON.valueToTree(builder.build());
  }

  public static TransactionValidationParams buildTransactionValidationParams() {
    return ImmutableTransactionValidationParams.builder()
        .from(TransactionValidationParams.transactionSimulator())
        .build();
  }

  public static TraceOptions buildTraceOptions(final Set<TraceTypeParameter.TraceType> traceTypes) {
    return new TraceOptions(
        traceTypes.contains(TraceTypeParameter.TraceType.STATE_DIFF),
        false,
        traceTypes.contains(TraceTypeParameter.TraceType.TRACE)
            || traceTypes.contains(TraceTypeParameter.TraceType.VM_TRACE));
  }
}
