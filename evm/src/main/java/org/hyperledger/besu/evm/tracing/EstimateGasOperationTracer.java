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

/** The Estimate gas operation tracer. */
public class EstimateGasOperationTracer implements OperationTracer {

  private int maxDepth = 0;

  private long sStoreStipendNeeded = 0L;

  /** Default constructor. */
  public EstimateGasOperationTracer() {}

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    if (frame.getCurrentOperation() instanceof SStoreOperation sStoreOperation
        && sStoreStipendNeeded == 0L) {
      sStoreStipendNeeded = sStoreOperation.getMinimumGasRemaining();
    }
    if (maxDepth < frame.getDepth()) {
      maxDepth = frame.getDepth();
    }
  }

  /**
   * Gets max depth.
   *
   * @return the max depth
   */
  public int getMaxDepth() {
    return maxDepth;
  }

  /**
   * Gets stipend needed.
   *
   * @return the stipend needed
   */
  public long getStipendNeeded() {
    return sStoreStipendNeeded;
  }
}
