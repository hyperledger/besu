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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public interface OperationTracer {

  OperationTracer NO_TRACING =
      ((frame, currentGasCost, executeOperation) -> executeOperation.execute());

  void traceExecution(
      MessageFrame frame, Optional<Gas> currentGasCost, ExecuteOperation executeOperation)
      throws ExceptionalHaltException;

  default void tracePrecompileCall(
      final MessageFrame frame, final Gas gasRequirement, final Bytes output) {};

  default void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {}

  interface ExecuteOperation {

    void execute() throws ExceptionalHaltException;
  }
}
