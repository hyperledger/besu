package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.function.Function;
import java.util.stream.Stream;

public class TraceFlatTransactionStep implements Function<TransactionTrace, Stream<Trace>> {

  private final ProtocolSchedule protocolSchedule;
  private final Block block;

  public TraceFlatTransactionStep(final ProtocolSchedule protocolSchedule, final Block block) {
    this.protocolSchedule = protocolSchedule;
    this.block = block;
  }

  @Override
  public Stream<Trace> apply(final TransactionTrace transactionTrace) {
    return FlatTraceGenerator.generateFromTransactionTraceAndBlock(
        protocolSchedule, transactionTrace, block);
  }
}
