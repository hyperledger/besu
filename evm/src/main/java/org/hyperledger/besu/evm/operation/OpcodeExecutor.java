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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

/**
 * Functional interface for opcode execution. Pre-built at EVM construction time to avoid runtime
 * dispatch overhead from the giant switch statement. Each opcode gets an executor that either calls
 * a static method (for hot opcodes) or delegates to the Operation instance (for complex opcodes).
 */
@FunctionalInterface
public interface OpcodeExecutor {

  /**
   * Execute an opcode.
   *
   * @param frame the message frame containing execution state
   * @param code the bytecode array
   * @param pc the current program counter
   * @param evm the EVM instance
   * @return the operation result containing gas cost, halt reason, and PC increment
   */
  OperationResult execute(MessageFrame frame, byte[] code, int pc, EVM evm);
}
