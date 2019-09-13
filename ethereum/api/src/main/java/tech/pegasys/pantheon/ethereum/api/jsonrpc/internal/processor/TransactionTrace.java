/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.debug.TraceFrame;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor.Result;

import java.util.List;

public class TransactionTrace {

  private final Transaction transaction;
  private final Result result;
  private final List<TraceFrame> traceFrames;

  public TransactionTrace(
      final Transaction transaction, final Result result, final List<TraceFrame> traceFrames) {
    this.transaction = transaction;
    this.result = result;
    this.traceFrames = traceFrames;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public long getGas() {
    return transaction.getGasLimit() - result.getGasRemaining();
  }

  public Result getResult() {
    return result;
  }

  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }
}
