package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor;

import java.util.List;

public class PrivateBlockTrace {

  private final List<PrivateTransactionTrace> transactionTraces;

  public PrivateBlockTrace(final List<PrivateTransactionTrace> transactionTraces) {
    this.transactionTraces = transactionTraces;
  }

  public List<PrivateTransactionTrace> getTransactionTraces() {
    return transactionTraces;
  }
}
