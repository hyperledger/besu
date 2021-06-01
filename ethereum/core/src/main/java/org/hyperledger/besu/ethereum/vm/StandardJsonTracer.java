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

package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.Operation.OperationResult;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class StandardJsonTracer implements OperationTracer {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final PrintStream out;
  private final boolean showMemory;

  public StandardJsonTracer(final PrintStream out, final boolean showMemory) {
    this.out = out;
    this.showMemory = showMemory;
  }

  public static String shortNumber(final UInt256 number) {
    return number.isZero() ? "0x0" : number.toShortHexString();
  }

  private static String shortBytes(final Bytes bytes) {
    return bytes.isZero() ? "0x0" : bytes.toShortHexString();
  }

  public static String summaryTrace(
      final Transaction transaction, final long timer, final TransactionProcessingResult result) {
    final ObjectNode summaryLine = OBJECT_MAPPER.createObjectNode();
    summaryLine.put("output", result.getOutput().toUnprefixedHexString());
    summaryLine.put(
        "gasUsed",
        StandardJsonTracer.shortNumber(
            UInt256.valueOf(transaction.getGasLimit() - result.getGasRemaining())));
    summaryLine.put("time", timer);
    return summaryLine.toString();
  }

  @Override
  public void traceExecution(
      final MessageFrame messageFrame, final ExecuteOperation executeOperation) {
    final ObjectNode traceLine = OBJECT_MAPPER.createObjectNode();

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
    Bytes returnData = messageFrame.getReturnData();
    traceLine.put("returnData", returnData.size() > 0 ? returnData.toHexString() : null);
    traceLine.put("depth", messageFrame.getMessageStackDepth() + 1);

    final OperationResult executeResult = executeOperation.execute();

    traceLine.put("refund", messageFrame.getGasRefund().toLong());
    traceLine.put(
        "gasCost", executeResult.getGasCost().map(gas -> shortNumber(gas.asUInt256())).orElse(""));

    if (showMemory) {
      traceLine.put(
          "memory",
          messageFrame
              .readMemory(UInt256.ZERO, messageFrame.memoryWordSize().multiply(32))
              .toHexString());
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
    out.println(traceLine);
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final Gas gasRequirement, final Bytes output) {}

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {}
}
