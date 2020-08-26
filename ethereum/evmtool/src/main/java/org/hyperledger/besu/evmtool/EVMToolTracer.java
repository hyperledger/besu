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
 *
 */

package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation;
import org.hyperledger.besu.ethereum.vm.Operation.OperationResult;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.operations.ReturnStack;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

class EVMToolTracer implements OperationTracer {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final PrintStream out;
  private final boolean showMemory;

  EVMToolTracer(final PrintStream out, final boolean showMemory) {
    this.out = out;
    this.showMemory = showMemory;
  }

  static String shortNumber(final UInt256 number) {
    return number.isZero() ? "0x0" : number.toShortHexString();
  }

  private static String shortBytes(final Bytes bytes) {
    return bytes.isZero() ? "0x0" : bytes.toShortHexString();
  }

  @Override
  public void traceExecution(
      final MessageFrame messageFrame, final ExecuteOperation executeOperation) {
    final ObjectNode traceLine = objectMapper.createObjectNode();

    final Operation currentOp = messageFrame.getCurrentOperation();
    traceLine.put("pc", messageFrame.getPC());
    traceLine.put("op", Bytes.of(currentOp.getOpcode()).toInt());
    traceLine.put("gas", shortNumber(messageFrame.getRemainingGas().asUInt256()));
    traceLine.putNull("gasCost");
    traceLine.putNull("memory");
    traceLine.putNull("memSize");
    final ArrayNode stack = traceLine.putArray("stack");
    for (int i = messageFrame.stackSize() - 1; i >= 0; i--) {
      stack.add(shortBytes(messageFrame.getStackItem(i)));
    }
    final ArrayNode returnStack = traceLine.putArray("returnStack");
    final ReturnStack rs = messageFrame.getReturnStack();
    for (int i = rs.size() - 1; i >= 0; i--) {
      returnStack.add(rs.get(i));
    }
    Bytes returnData = messageFrame.getReturnData();
    traceLine.put("returnData", returnData.size() > 0 ? returnData.toHexString() : null);
    traceLine.put("depth", messageFrame.getMessageStackDepth() + 1);
    traceLine.put("refund", messageFrame.getGasRefund().toLong());

    final OperationResult executeResult = executeOperation.execute();
    traceLine.put(
        "gasCost", executeResult.getGasCost().map(gas -> shortNumber(gas.asUInt256())).orElse(""));
    if (showMemory) {
      traceLine.put(
          "memory",
          messageFrame.readMemory(UInt256.ZERO, messageFrame.memoryWordSize()).toHexString());
    } else {
      traceLine.put("memory", "0x");
    }
    traceLine.put("memSize", messageFrame.memoryByteSize());

    final String error =
        executeResult
            .getHaltReason()
            .map(ExceptionalHaltReason::getDescription)
            .orElse(
                messageFrame
                    .getRevertReason()
                    .map(bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8))
                    .orElse(""));
    traceLine.put("opName", currentOp.getName());
    traceLine.put("error", error);
    out.println(traceLine.toString());
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final Gas gasRequirement, final Bytes output) {}

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {}
}
