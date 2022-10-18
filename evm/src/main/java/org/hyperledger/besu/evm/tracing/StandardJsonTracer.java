/*
 * Copyright contributors to Hyperledger Besu
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

package org.hyperledger.besu.evm.tracing;

import static com.google.common.base.Strings.padStart;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Joiner;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class StandardJsonTracer implements OperationTracer {

  private final PrintStream out;
  private final boolean showMemory;

  public StandardJsonTracer(final PrintStream out, final boolean showMemory) {
    this.out = out;
    this.showMemory = showMemory;
  }

  public static String shortNumber(final UInt256 number) {
    return number.isZero() ? "0x0" : number.toShortHexString();
  }

  public static String shortNumber(final long number) {
    return "0x" + Long.toHexString(number);
  }

  private static String shortBytes(final Bytes bytes) {
    return bytes.isZero() ? "0x0" : bytes.toShortHexString();
  }

  final Joiner commaJoiner = Joiner.on(',');

  private List<String> preExecuteStack;

  @Override
  public void tracePreExecution(final MessageFrame messageFrame) {
    preExecuteStack = new ArrayList<>(messageFrame.stackSize());
    for (int i = messageFrame.stackSize() - 1; i >= 0; i--) {
      preExecuteStack.add("\"" + shortBytes(messageFrame.getStackItem(i)) + "\"");
    }
  }

  @Override
  public void tracePostExecution(
      final MessageFrame messageFrame, final Operation.OperationResult executeResult) {
    final Operation currentOp = messageFrame.getCurrentOperation();
    final int pc = messageFrame.getPC();
    final int opcode = currentOp.getOpcode();
    final String remainingGas = shortNumber(messageFrame.getRemainingGas());
    final Bytes returnData = messageFrame.getReturnData();
    final int depth = messageFrame.getMessageStackDepth() + 1;

    final StringBuilder sb = new StringBuilder(1024);
    sb.append("{");
    sb.append("\"pc\":").append(pc).append(",");
    sb.append("\"op\":").append(opcode).append(",");
    sb.append("\"gas\":\"").append(remainingGas).append("\",");
    sb.append("\"gasCost\":\"")
        .append(executeResult.getGasCost() != 0 ? shortNumber(executeResult.getGasCost()) : "")
        .append("\",");
    if (showMemory) {
      final Bytes memory = messageFrame.readMemory(0, messageFrame.memoryWordSize() * 32L);
      sb.append("\"memory\":\"").append(memory.toHexString()).append("\",");
      sb.append("\"memSize\":").append(memory.size()).append(",");
    } else {
      sb.append("\"memory\":\"0x\",");
      sb.append("\"memSize\":").append(messageFrame.memoryByteSize()).append(",");
    }
    sb.append("\"stack\":[").append(commaJoiner.join(preExecuteStack)).append("],");
    sb.append("\"returnData\":")
        .append(returnData.size() > 0 ? '"' + returnData.toHexString() + '"' : "null")
        .append(",");
    sb.append("\"depth\":").append(depth).append(",");
    sb.append("\"refund\":").append(messageFrame.getGasRefund()).append(",");
    sb.append("\"opName\":\"").append(currentOp.getName()).append("\",");
    sb.append("\"error\":\"")
        .append(
            executeResult.getHaltReason() == null
                ? (quoteEscape(messageFrame.getRevertReason().orElse(Bytes.EMPTY)))
                : executeResult.getHaltReason().getDescription())
        .append("\"}");
    out.println(sb);
  }

  static String quoteEscape(final Bytes bytes) {
    final StringBuilder result = new StringBuilder(bytes.size());
    for (final byte b : bytes.toArrayUnsafe()) {
      final int c = Byte.toUnsignedInt(b);
      // list from RFC-4627 section 2
      if (c == '"') {
        result.append("\\\"");
      } else if (c == '\\') {
        result.append("\\\\");
      } else if (c == '/') {
        result.append("\\/");
      } else if (c == '\b') {
        result.append("\\b");
      } else if (c == '\f') {
        result.append("\\f");
      } else if (c == '\n') {
        result.append("\\n");
      } else if (c == '\r') {
        result.append("\\r");
      } else if (c == '\t') {
        result.append("\\t");
      } else if (c <= 0x1F) {
        result.append("\\u");
        result.append(padStart(Integer.toHexString(c), 4, '0'));
      } else {
        result.append((char) b);
      }
    }
    return result.toString();
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    // precompile calls are not part of the standard trace
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    // precompile calls are not part of the standard trace
  }
}
