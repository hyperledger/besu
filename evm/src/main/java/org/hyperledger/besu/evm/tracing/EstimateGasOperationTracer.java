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

/** The Estimate gas operation tracer. */
public class EstimateGasOperationTracer implements OperationTracer {

  private static final long callStipend = 2_300L;

  /** Default constructor. */
  public EstimateGasOperationTracer() {}

  /**
   * Gets call stipend.
   *
   * @return the call stipend
   */
  public long getCallStipend() {
    return callStipend;
  }

  /**
   * Gets the sub call exponent
   *
   * @return the sub call exponent
   */
  public int getSubCallExponent() {
    return 1;
  }
}
