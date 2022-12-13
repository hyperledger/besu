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
package org.hyperledger.besu.evm.tracing;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.SStoreOperation;

public class EstimateGasOperationTracer implements OperationTracer {
  private static final double SUB_CALL_REMAINING_GAS_RATIO = 65D / 64D;

  private int maxDepth = 0;

  private long sStoreStipendNeeded = 0L;

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    if (frame.getCurrentOperation() instanceof SStoreOperation && sStoreStipendNeeded == 0L) {
      sStoreStipendNeeded =
          ((SStoreOperation) frame.getCurrentOperation()).getMinimumGasRemaining();
    }
    if (maxDepth < frame.getMessageStackDepth()) {
      maxDepth = frame.getMessageStackDepth();
    }
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public long getStipendNeeded() {
    return sStoreStipendNeeded;
  }

  /**
   * Estimate gas by adding minimum gas remaining for some operation and the necessary gas for sub
   * calls
   *
   * @param gasUsedByTransaction The gas Used By Transaction
   * @return estimate gas
   */
  public long calculateEstimateGas(final long gasUsedByTransaction) {
    // no more than 63/64s of the remaining gas can be passed to the sub calls
    final double subCallMultiplier = Math.pow(SUB_CALL_REMAINING_GAS_RATIO, getMaxDepth());
    // and minimum gas remaining is necessary for some operation (additionalStipend)
    final long gasStipend = getStipendNeeded();
    return ((long) ((gasUsedByTransaction + gasStipend) * subCallMultiplier));
  }
}
