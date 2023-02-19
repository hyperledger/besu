package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.util.ArrayNodeWrapper;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.function.Consumer;

public class BuildArrayNodeCompleterStep implements Consumer<Trace> {

  private final ArrayNodeWrapper resultArrayNode;

  public BuildArrayNodeCompleterStep(final ArrayNodeWrapper resultArrayNode) {
    this.resultArrayNode = resultArrayNode;
  }

  @Override
  public void accept(final Trace trace) {
    resultArrayNode.addPOJO(trace);
  }
}
