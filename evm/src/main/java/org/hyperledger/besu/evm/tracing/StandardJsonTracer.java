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
package org.hyperledger.besu.evm.tracing;

import org.hyperledger.besu.evm.code.OpcodeInfo;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractCallOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Joiner;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** The Standard json tracer. */
public class StandardJsonTracer implements OperationTracer {

  private static final Joiner commaJoiner = Joiner.on(',');
  private final PrintWriter out;
  private final boolean showMemory;
  private final boolean showStack;
  private final boolean showReturnData;
  private final boolean showStorage;
  private int pc;
  private int section;
  private List<String> stack;
  private String gas;
  private Bytes memory;
  private int memorySize;
  private int depth;
  private int subdepth;
  private String storageString;

  /**
   * Instantiates a new Standard json tracer.
   *
   * @param out the out
   * @param showMemory show memory in trace lines
   * @param showStack show the stack in trace lines
   * @param showReturnData show return data in trace lines
   * @param showStorage show the updated storage
   */
  public StandardJsonTracer(
      final PrintWriter out,
      final boolean showMemory,
      final boolean showStack,
      final boolean showReturnData,
      final boolean showStorage) {
    this.out = out;
    this.showMemory = showMemory;
    this.showStack = showStack;
    this.showReturnData = showReturnData;
    this.showStorage = showStorage;
  }

  /**
   * Instantiates a new Standard json tracer.
   *
   * @param out the out
   * @param showMemory show memory in trace lines
   * @param showStack show the stack in trace lines
   * @param showReturnData show return data in trace lines
   * @param showStorage show updated storage
   */
  public StandardJsonTracer(
      final PrintStream out,
      final boolean showMemory,
      final boolean showStack,
      final boolean showReturnData,
      final boolean showStorage) {
    this(
        new PrintWriter(out, true, StandardCharsets.UTF_8),
        showMemory,
        showStack,
        showReturnData,
        showStorage);
  }

  /**
   * Short as hex string.
   *
   * @param number the number
   * @return the string
   */
  public static String shortNumber(final UInt256 number) {
    return number.isZero() ? "0x0" : number.toShortHexString();
  }

  /**
   * Long number as hex string.
   *
   * @param number the number
   * @return the string
   */
  public static String shortNumber(final long number) {
    return "0x" + Long.toHexString(number);
  }

  private static String shortBytes(final Bytes bytes) {
    return bytes.isZero() ? "0x0" : bytes.toShortHexString();
  }

  @Override
  public void tracePreExecution(final MessageFrame messageFrame) {
    stack = new ArrayList<>(messageFrame.stackSize());
    for (int i = messageFrame.stackSize() - 1; i >= 0; i--) {
      stack.add("\"" + shortBytes(messageFrame.getStackItem(i)) + "\"");
    }
    pc = messageFrame.getPC() - messageFrame.getCode().getCodeSection(0).getEntryPoint();
    section = messageFrame.getSection();
    gas = shortNumber(messageFrame.getRemainingGas());
    memorySize = messageFrame.memoryWordSize() * 32;
    if (showMemory && memorySize > 0) {
      memory = messageFrame.readMemory(0, messageFrame.memoryWordSize() * 32L);
    } else {
      memory = null;
    }
    depth = messageFrame.getMessageStackSize();
    subdepth = messageFrame.returnStackSize();

    StringBuilder sb = new StringBuilder();
    if (showStorage) {
      var updater = messageFrame.getWorldUpdater();
      var account = updater.getAccount(messageFrame.getRecipientAddress());
      if (account != null && !account.getUpdatedStorage().isEmpty()) {
        boolean[] shownEntry = {false};
        sb.append(",\"storage\":{");
        account
            .getUpdatedStorage()
            .forEach(
                (k, v) -> {
                  if (shownEntry[0]) {
                    sb.append(",");
                  } else {
                    shownEntry[0] = true;
                  }
                  sb.append("\"")
                      .append(k.toQuantityHexString())
                      .append("\":\"")
                      .append(v.toQuantityHexString())
                      .append("\"");
                });
        sb.append("}");
      }
    }
    storageString = sb.toString();
  }

  @Override
  public void tracePostExecution(
      final MessageFrame messageFrame, final Operation.OperationResult executeResult) {
    final Operation currentOp = messageFrame.getCurrentOperation();
    if (currentOp.isVirtualOperation()) {
      return;
    }
    final int opcode = currentOp.getOpcode();
    final Bytes returnData = messageFrame.getReturnData();
    long thisGasCost = executeResult.getGasCost();
    if (currentOp instanceof AbstractCallOperation) {
      thisGasCost += messageFrame.getMessageFrameStack().getFirst().getRemainingGas();
    }

    final StringBuilder sb = new StringBuilder(1024);
    sb.append("{");
    sb.append("\"pc\":").append(pc).append(",");
    boolean eofContract = messageFrame.getCode().getEofVersion() > 0;
    if (eofContract) {
      sb.append("\"section\":").append(section).append(",");
    }
    sb.append("\"op\":").append(opcode).append(",");
    OpcodeInfo opInfo = OpcodeInfo.getOpcode(opcode);
    if (eofContract && opInfo.pcAdvance() > 1) {
      var immediate =
          messageFrame
              .getCode()
              .getBytes()
              .slice(
                  pc + messageFrame.getCode().getCodeSection(0).getEntryPoint() + 1,
                  opInfo.pcAdvance() - 1);
      sb.append("\"immediate\":\"").append(immediate.toHexString()).append("\",");
    }
    sb.append("\"gas\":\"").append(gas).append("\",");
    sb.append("\"gasCost\":\"").append(shortNumber(thisGasCost)).append("\",");
    if (memory != null) {
      sb.append("\"memory\":\"").append(memory.toHexString()).append("\",");
    }
    sb.append("\"memSize\":").append(memorySize).append(",");
    if (showStack) {
      sb.append("\"stack\":[").append(commaJoiner.join(stack)).append("],");
    }
    if (showReturnData && !returnData.isEmpty()) {
      sb.append("\"returnData\":\"").append(returnData.toHexString()).append("\",");
    }
    sb.append("\"depth\":").append(depth).append(",");
    if (subdepth >= 1) {
      sb.append("\"functionDepth\":").append(subdepth).append(",");
    }
    sb.append("\"refund\":").append(messageFrame.getGasRefund()).append(",");
    sb.append("\"opName\":\"").append(currentOp.getName()).append("\"");
    if (executeResult.getHaltReason() != null) {
      sb.append(",\"error\":\"")
          .append(executeResult.getHaltReason().getDescription())
          .append("\"");
    } else if (messageFrame.getRevertReason().isPresent()) {
      sb.append(",\"error\":\"")
          .append(messageFrame.getRevertReason().get().toHexString())
          .append("\"");
    }

    sb.append(storageString).append("}");
    out.println(sb);
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
