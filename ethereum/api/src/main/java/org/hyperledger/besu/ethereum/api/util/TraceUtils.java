package org.hyperledger.besu.ethereum.api.util;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter.TraceType.VM_TRACE;

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
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
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
}
