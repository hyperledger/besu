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
package org.hyperledger.besu.evm.code;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode.INITCODE;
import static org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode.RUNTIME;
import static org.hyperledger.besu.evm.code.OpcodeInfo.V1_OPCODES;
import static org.hyperledger.besu.evm.internal.Words.readBigEndianI16;
import static org.hyperledger.besu.evm.internal.Words.readBigEndianU16;

import org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode;
import org.hyperledger.besu.evm.operation.CallFOperation;
import org.hyperledger.besu.evm.operation.DataLoadNOperation;
import org.hyperledger.besu.evm.operation.DupNOperation;
import org.hyperledger.besu.evm.operation.EOFCreateOperation;
import org.hyperledger.besu.evm.operation.ExchangeOperation;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.JumpFOperation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpIfOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpVectorOperation;
import org.hyperledger.besu.evm.operation.RetFOperation;
import org.hyperledger.besu.evm.operation.ReturnContractOperation;
import org.hyperledger.besu.evm.operation.ReturnOperation;
import org.hyperledger.besu.evm.operation.RevertOperation;
import org.hyperledger.besu.evm.operation.StopOperation;
import org.hyperledger.besu.evm.operation.SwapNOperation;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Queue;
import javax.annotation.Nullable;

import org.apache.tuweni.bytes.Bytes;

/** Code V1 Validation */
public class CodeV1Validation implements EOFValidator {

  static final int MAX_STACK_HEIGHT = 1024;

  /** Maximum size of the code stream that can be produced, including all header bytes. */
  protected final int maxContainerSize;

  /**
   * Create a new container, with a configurable maximim container size.
   *
   * @param maxContainerSize the maximum size of any container.
   */
  public CodeV1Validation(final int maxContainerSize) {
    this.maxContainerSize = maxContainerSize;
  }

  /**
   * Validates the code and stack for the EOF Layout, with optional deep consideration of the
   * containers.
   *
   * @param layout The parsed EOFLayout of the code
   * @return either null, indicating no error, or a String describing the validation error.
   */
  @SuppressWarnings(
      "ReferenceEquality") // comparison `container != layout` is deliberate and correct
  @Override
  public String validate(final EOFLayout layout) {
    if (layout.container().size() > maxContainerSize) {
      return "container_size_above_limit of " + maxContainerSize;
    }

    Queue<EOFLayout> workList = new ArrayDeque<>(layout.getSubcontainerCount());
    workList.add(layout);

    while (!workList.isEmpty()) {
      EOFLayout container = workList.poll();
      workList.addAll(List.of(container.subContainers()));
      if (container != layout && container.containerMode().get() == null) {
        return "orphan_subcontainer #" + layout.indexOfSubcontainer(container);
      }
      if (container.containerMode().get() != RUNTIME
          && container.data().size() != container.dataLength()) {
        return "Incomplete data section "
            + (container == layout
                ? " at root"
                : " in container #" + layout.indexOfSubcontainer(container));
      }

      final String codeValidationError = validateCode(container);
      if (codeValidationError != null) {
        return codeValidationError;
      }

      final String stackValidationError = validateStack(container);
      if (stackValidationError != null) {
        return stackValidationError;
      }
    }

    return null;
  }

  /**
   * Validate Code
   *
   * @param eofLayout The EOF Layout
   * @return validation code, null otherwise.
   */
  @Override
  public String validateCode(final EOFLayout eofLayout) {
    if (!eofLayout.isValid()) {
      return "Invalid EOF container - " + eofLayout.invalidReason();
    }
    for (CodeSection cs : eofLayout.codeSections()) {
      var validation =
          validateCode(
              eofLayout.container().slice(cs.getEntryPoint(), cs.getLength()), cs, eofLayout);
      if (validation != null) {
        return validation;
      }
    }
    return null;
  }

  /**
   * validates the code section
   *
   * @param code the code section code
   * @return null if valid, otherwise a string containing an error reason.
   */
  String validateCode(
      final Bytes code, final CodeSection thisCodeSection, final EOFLayout eofLayout) {
    final int size = code.size();
    final BitSet rjumpdests = new BitSet(size);
    final BitSet immediates = new BitSet(size);
    final byte[] rawCode = code.toArrayUnsafe();
    OpcodeInfo opcodeInfo = V1_OPCODES[0xfe];
    int pos = 0;
    EOFContainerMode eofContainerMode = eofLayout.containerMode().get();
    boolean hasReturningOpcode = false;
    while (pos < size) {
      final int operationNum = rawCode[pos] & 0xff;
      opcodeInfo = V1_OPCODES[operationNum];
      if (!opcodeInfo.valid()) {
        // undefined instruction
        return format("undefined_instruction 0x%02x", operationNum);
      }
      pos += 1;
      int pcPostInstruction = pos;
      switch (operationNum) {
        case StopOperation.OPCODE, ReturnOperation.OPCODE:
          if (eofContainerMode == null) {
            eofContainerMode = RUNTIME;
            eofLayout.containerMode().set(RUNTIME);
          } else if (!eofContainerMode.equals(RUNTIME)) {
            return format(
                "incompatible_container_kind opcode %s is only valid for runtime.",
                opcodeInfo.name());
          }
          break;
        case PushOperation.PUSH_BASE,
            PushOperation.PUSH_BASE + 1,
            PushOperation.PUSH_BASE + 2,
            PushOperation.PUSH_BASE + 3,
            PushOperation.PUSH_BASE + 4,
            PushOperation.PUSH_BASE + 5,
            PushOperation.PUSH_BASE + 6,
            PushOperation.PUSH_BASE + 7,
            PushOperation.PUSH_BASE + 8,
            PushOperation.PUSH_BASE + 9,
            PushOperation.PUSH_BASE + 10,
            PushOperation.PUSH_BASE + 11,
            PushOperation.PUSH_BASE + 12,
            PushOperation.PUSH_BASE + 13,
            PushOperation.PUSH_BASE + 14,
            PushOperation.PUSH_BASE + 15,
            PushOperation.PUSH_BASE + 16,
            PushOperation.PUSH_BASE + 17,
            PushOperation.PUSH_BASE + 18,
            PushOperation.PUSH_BASE + 19,
            PushOperation.PUSH_BASE + 20,
            PushOperation.PUSH_BASE + 21,
            PushOperation.PUSH_BASE + 22,
            PushOperation.PUSH_BASE + 23,
            PushOperation.PUSH_BASE + 24,
            PushOperation.PUSH_BASE + 25,
            PushOperation.PUSH_BASE + 26,
            PushOperation.PUSH_BASE + 27,
            PushOperation.PUSH_BASE + 28,
            PushOperation.PUSH_BASE + 29,
            PushOperation.PUSH_BASE + 30,
            PushOperation.PUSH_BASE + 31,
            PushOperation.PUSH_BASE + 32:
          final int multiByteDataLen = operationNum - PushOperation.PUSH_BASE;
          pcPostInstruction += multiByteDataLen;
          break;
        case DataLoadNOperation.OPCODE:
          if (pos + 2 > size) {
            return "truncated_instruction DATALOADN";
          }
          pcPostInstruction += 2;
          final int dataLoadOffset = readBigEndianU16(pos, rawCode);
          // only verfy the last byte of the load is within the minimum data
          if (dataLoadOffset > eofLayout.dataLength() - 32) {
            return "invalid_dataloadn_index %d + 32 > %d"
                .formatted(dataLoadOffset, eofLayout.dataLength());
          }
          break;
        case RelativeJumpOperation.OPCODE, RelativeJumpIfOperation.OPCODE:
          if (pos + 2 > size) {
            return "truncated_instruction RJUMP";
          }
          pcPostInstruction += 2;
          final int offset = readBigEndianI16(pos, rawCode);
          final int rjumpdest = pcPostInstruction + offset;
          if (rjumpdest < 0 || rjumpdest >= size) {
            return "invalid_rjump_destination out of bounds";
          }
          rjumpdests.set(rjumpdest);
          break;
        case RelativeJumpVectorOperation.OPCODE:
          pcPostInstruction += 1;
          if (pcPostInstruction > size) {
            return "truncated_instruction RJUMPV";
          }
          int jumpBasis = pcPostInstruction;
          final int jumpTableSize = RelativeJumpVectorOperation.getVectorSize(code, pos);
          pcPostInstruction += 2 * jumpTableSize;
          if (pcPostInstruction > size) {
            return "truncated_instruction RJUMPV";
          }
          for (int offsetPos = jumpBasis; offsetPos < pcPostInstruction; offsetPos += 2) {
            final int rjumpvOffset = readBigEndianI16(offsetPos, rawCode);
            final int rjumpvDest = pcPostInstruction + rjumpvOffset;
            if (rjumpvDest < 0 || rjumpvDest >= size) {
              return "invalid_rjump_destination out of bounds";
            }
            rjumpdests.set(rjumpvDest);
          }
          break;
        case CallFOperation.OPCODE:
          if (pos + 2 > size) {
            return "truncated_instruction CALLF";
          }
          int section = readBigEndianU16(pos, rawCode);
          if (section >= eofLayout.getCodeSectionCount()) {
            return "invalid_code_section_index CALLF to " + Integer.toHexString(section);
          }
          if (!eofLayout.getCodeSection(section).returning) {
            return "CALLF to non-returning section - " + Integer.toHexString(section);
          }
          pcPostInstruction += 2;
          break;
        case RetFOperation.OPCODE:
          hasReturningOpcode = true;
          break;
        case JumpFOperation.OPCODE:
          if (pos + 2 > size) {
            return "truncated_instruction JUMPF";
          }
          int targetSection = readBigEndianU16(pos, rawCode);
          if (targetSection >= eofLayout.getCodeSectionCount()) {
            return "invalid_code_section_index JUMPF - " + Integer.toHexString(targetSection);
          }
          CodeSection targetCodeSection = eofLayout.getCodeSection(targetSection);
          if (targetCodeSection.isReturning() && !thisCodeSection.isReturning()) {
            return "invalid_non_returning_flag non-returning JUMPF source must have non-returning target";
          } else if (thisCodeSection.getOutputs() < targetCodeSection.getOutputs()) {
            return format(
                "jumpf_destination_incompatible_outputs target %2x with more outputs %d than current section's outputs %d",
                targetSection, targetCodeSection.getOutputs(), thisCodeSection.getOutputs());
          }
          hasReturningOpcode |= eofLayout.getCodeSection(targetSection).isReturning();
          pcPostInstruction += 2;
          break;
        case EOFCreateOperation.OPCODE:
          if (pos + 1 > size) {
            return format(
                "truncated_instruction dangling immediate for %s at pc=%d",
                opcodeInfo.name(), pos - opcodeInfo.pcAdvance());
          }
          int subcontainerNum = rawCode[pos] & 0xff;
          if (subcontainerNum >= eofLayout.getSubcontainerCount()) {
            return format(
                "invalid_container_section_index %s refers to non-existent subcontainer %d at pc=%d",
                opcodeInfo.name(), subcontainerNum, pos - opcodeInfo.pcAdvance());
          }
          EOFLayout subContainer = eofLayout.getSubcontainer(subcontainerNum);
          var subcontainerMode = subContainer.containerMode().get();
          if (subcontainerMode == null) {
            subContainer.containerMode().set(INITCODE);
          } else if (subcontainerMode == RUNTIME) {
            return format(
                "incompatible_container_kind subcontainer %d should be initcode", subcontainerNum);
          }
          if (subContainer.dataLength() != subContainer.data().size()) {
            return format(
                "A subcontainer used for %s has a truncated data section, expected %d and is %d.",
                V1_OPCODES[operationNum].name(),
                subContainer.dataLength(),
                subContainer.data().size());
          }
          pcPostInstruction += 1;
          break;
        case ReturnContractOperation.OPCODE:
          if (eofContainerMode == null) {
            eofContainerMode = INITCODE;
            eofLayout.containerMode().set(INITCODE);
          } else if (!eofContainerMode.equals(INITCODE)) {
            return format(
                "incompatible_container_kind opcode %s is only valid for initcode",
                opcodeInfo.name());
          }
          if (pos + 1 > size) {
            return format(
                "truncated_instruction dangling immediate for %s at pc=%d",
                opcodeInfo.name(), pos - opcodeInfo.pcAdvance());
          }
          int returnedContractNum = rawCode[pos] & 0xff;
          if (returnedContractNum >= eofLayout.getSubcontainerCount()) {
            return format(
                "invalid_container_section_index %s refers to non-existent subcontainer %d at pc=%d",
                opcodeInfo.name(), returnedContractNum, pos - opcodeInfo.pcAdvance());
          }
          EOFLayout returnedContract = eofLayout.getSubcontainer(returnedContractNum);
          var returnedContractMode = returnedContract.containerMode().get();
          if (returnedContractMode == null) {
            returnedContract.containerMode().set(RUNTIME);
          } else if (returnedContractMode.equals(INITCODE)) {
            return format(
                "incompatible_container_kind subcontainer %d should be runtime",
                returnedContractNum);
          }
          pcPostInstruction += 1;
          break;
        default:
          // a few opcodes have potentially dangling immediates
          if (opcodeInfo.pcAdvance() > 1) {
            pcPostInstruction += opcodeInfo.pcAdvance() - 1;
            if (pcPostInstruction > size) {
              return format(
                  "truncated_instruction dangling immediate for %s at pc=%d",
                  opcodeInfo.name(), pos - opcodeInfo.pcAdvance());
            }
          }
          break;
      }
      immediates.set(pos, pcPostInstruction);
      pos = pcPostInstruction;
    }
    if (thisCodeSection.isReturning() != hasReturningOpcode) {
      return thisCodeSection.isReturning()
          ? "unreachable_code_sections no RETF or qualifying JUMPF"
          : "invalid_non_returning_flag RETF or JUMPF into returning section";
    }
    if (!opcodeInfo.terminal()) {
      return "missing_stop_opcode No terminating instruction";
    }
    if (rjumpdests.intersects(immediates)) {
      return "invalid_rjump_destination targets immediate data";
    }
    return null;
  }

  @Nullable
  @Override
  public String validateStack(final EOFLayout eofLayout) {
    WorkList workList = new WorkList(eofLayout.getCodeSectionCount());
    workList.put(0);
    int sectionToValidatie = workList.take();
    while (sectionToValidatie >= 0) {
      var validation = validateStack(sectionToValidatie, eofLayout, workList);
      if (validation != null) {
        return validation;
      }
      sectionToValidatie = workList.take();
    }
    if (!workList.isComplete()) {
      return format("Unreachable code section %d", workList.getFirstUnmarkedItem());
    }
    return null;
  }

  /**
   * Validates the stack heights per <a href="https://eips.ethereum.org/EIPS/eip-5450">EIP-5450</a>.
   *
   * <p>This presumes code validation has already been performed, so there are no RJUMPS into
   * immediates as well as no immediates falling off of the end of code sections.
   *
   * @param codeSectionToValidate The index of code to validate in the code sections
   * @param eofLayout The EOF container to validate
   * @param workList The list of code sections needing validation
   * @return null if valid, otherwise an error string providing the validation error.
   */
  @Nullable
  String validateStack(
      final int codeSectionToValidate, final EOFLayout eofLayout, final WorkList workList) {
    if (!eofLayout.isValid()) {
      return "EOF Layout invalid - " + eofLayout.invalidReason();
    }
    try {
      CodeSection toValidate = eofLayout.getCodeSection(codeSectionToValidate);
      byte[] code =
          eofLayout.container().slice(toValidate.entryPoint, toValidate.length).toArrayUnsafe();
      int codeLength = code.length;
      int[] stack_min = new int[codeLength];
      int[] stack_max = new int[codeLength];
      Arrays.fill(stack_min, 1025);
      Arrays.fill(stack_max, -1);

      int initialStackHeight = toValidate.getInputs();
      int maxStackHeight = initialStackHeight;
      stack_min[0] = initialStackHeight;
      stack_max[0] = initialStackHeight;
      int unusedBytes = codeLength;

      int currentPC = 0;
      int currentMin = initialStackHeight;
      int currentMax = initialStackHeight;

      while (currentPC < codeLength) {
        int thisOp = code[currentPC] & 0xff;

        OpcodeInfo opcodeInfo = V1_OPCODES[thisOp];
        int stackInputs;
        int stackOutputs;
        int sectionStackUsed;
        int pcAdvance = opcodeInfo.pcAdvance();
        switch (thisOp) {
          case CallFOperation.OPCODE:
            int section = readBigEndianU16(currentPC + 1, code);
            workList.put(section);
            CodeSection codeSection = eofLayout.getCodeSection(section);
            stackInputs = codeSection.getInputs();
            stackOutputs = codeSection.getOutputs();
            sectionStackUsed = codeSection.getMaxStackHeight();
            break;
          case DupNOperation.OPCODE:
            int depth = code[currentPC + 1] & 0xff;
            stackInputs = depth + 1;
            stackOutputs = depth + 2;
            sectionStackUsed = 0;
            break;
          case SwapNOperation.OPCODE:
            int swapDepth = 2 + (code[currentPC + 1] & 0xff);
            stackInputs = swapDepth;
            stackOutputs = swapDepth;
            sectionStackUsed = 0;
            break;
          case ExchangeOperation.OPCODE:
            int imm = code[currentPC + 1] & 0xff;
            int exchangeDepth = (imm >> 4) + (imm & 0xf) + 3;
            stackInputs = exchangeDepth;
            stackOutputs = exchangeDepth;
            sectionStackUsed = 0;
            break;
          default:
            stackInputs = opcodeInfo.inputs();
            stackOutputs = opcodeInfo.outputs();
            sectionStackUsed = 0;
        }

        int nextPC;
        if (!opcodeInfo.valid()) {
          return format("undefined_instruction 0x%02x", thisOp);
        }
        nextPC = currentPC + pcAdvance;

        if (nextPC > codeLength) {
          return format(
              "Dangling immediate argument for opcode 0x%x at PC %d in code section %d.",
              thisOp, currentPC - pcAdvance, codeSectionToValidate);
        }
        if (stack_max[currentPC] < 0) {
          return format(
              "unreachable_instructions section 0x%x pc %d was not forward referenced",
              codeSectionToValidate, currentPC);
        }
        currentMin = min(stack_min[currentPC], currentMin);
        currentMax = max(stack_max[currentPC], currentMax);

        if (stackInputs > currentMin) {
          return format(
              "stack_underflow operation 0x%02X wants stack of %d but may only have %d",
              thisOp, stackInputs, currentMin);
        }

        int stackDelta = stackOutputs - stackInputs;
        currentMax = currentMax + stackDelta;
        currentMin = currentMin + stackDelta;
        if (currentMax + sectionStackUsed - stackOutputs > MAX_STACK_HEIGHT) {
          return "Stack height exceeds 1024";
        }

        unusedBytes -= pcAdvance;
        maxStackHeight = max(maxStackHeight, currentMax);

        switch (thisOp) {
          case RelativeJumpOperation.OPCODE:
            int jValue = readBigEndianI16(currentPC + 1, code);
            int targetPC = nextPC + jValue;
            if (targetPC > currentPC) {
              stack_min[targetPC] = min(stack_min[targetPC], currentMin);
              stack_max[targetPC] = max(stack_max[targetPC], currentMax);
            } else {
              if (stack_min[targetPC] != currentMin) {
                return format(
                    "stack_height_mismatch backwards RJUMP from %d to %d, min %d != %d",
                    currentPC, targetPC, stack_min[targetPC], currentMin);
              }
              if (stack_max[targetPC] != currentMax) {
                return format(
                    "stack_height_mismatch backwards RJUMP from %d to %d, max %d != %d",
                    currentPC, targetPC, stack_max[targetPC], currentMax);
              }
            }

            // terminal op, reset currentMin and currentMax to forward set values
            if (nextPC < codeLength) {
              currentMax = stack_max[nextPC];
              currentMin = stack_min[nextPC];
            }
            break;
          case RelativeJumpIfOperation.OPCODE:
            stack_max[nextPC] = max(stack_max[nextPC], currentMax);
            stack_min[nextPC] = min(stack_min[nextPC], currentMin);
            int jiValue = readBigEndianI16(currentPC + 1, code);
            int targetPCi = nextPC + jiValue;
            if (targetPCi > currentPC) {
              stack_min[targetPCi] = min(stack_min[targetPCi], currentMin);
              stack_max[targetPCi] = max(stack_max[targetPCi], currentMax);
            } else {
              if (stack_min[targetPCi] != currentMin) {
                return format(
                    "stack_height_mismatch backwards RJUMPI from %d to %d, min %d != %d",
                    currentPC, targetPCi, stack_min[targetPCi], currentMin);
              }
              if (stack_max[targetPCi] != currentMax) {
                return format(
                    "stack_height_mismatch backwards RJUMPI from %d to %d, max %d != %d",
                    currentPC, targetPCi, stack_max[targetPCi], currentMax);
              }
            }
            break;
          case RelativeJumpVectorOperation.OPCODE:
            int immediateDataSize = (code[currentPC + 1] & 0xff) * 2;
            unusedBytes -= immediateDataSize + 2;
            int tableEnd = immediateDataSize + currentPC + 4;
            nextPC = tableEnd;
            stack_max[nextPC] = max(stack_max[nextPC], currentMax);
            stack_min[nextPC] = min(stack_min[nextPC], currentMin);
            for (int i = currentPC + 2; i < tableEnd; i += 2) {
              int vValue = readBigEndianI16(i, code);
              int targetPCv = tableEnd + vValue;
              if (targetPCv > currentPC) {
                stack_min[targetPCv] = min(stack_min[targetPCv], currentMin);
                stack_max[targetPCv] = max(stack_max[targetPCv], currentMax);
              } else {
                if (stack_min[targetPCv] != currentMin) {
                  return format(
                      "stack_height_mismatch backwards RJUMPV from %d to %d, min %d != %d",
                      currentPC, targetPCv, stack_min[targetPCv], currentMin);
                }
                if (stack_max[targetPCv] != currentMax) {
                  return format(
                      "stack_height_mismatch backwards RJUMPV from %d to %d, max %d != %d",
                      currentPC, targetPCv, stack_max[targetPCv], currentMax);
                }
              }
            }
            break;
          case RetFOperation.OPCODE:
            int returnStackItems = toValidate.getOutputs();
            if (currentMin != currentMax) {
              return format(
                  "RETF in section %d has a stack range (%d/%d)and must have only one stack value",
                  codeSectionToValidate, currentMin, currentMax);
            }
            if (stack_min[currentPC] != returnStackItems
                || stack_min[currentPC] != stack_max[currentPC]) {
              return format(
                  "stack_higher_than_outputs RETF in section %d calculated height %d does not match configured return stack %d, min height %d, and max height %d",
                  codeSectionToValidate,
                  currentMin,
                  returnStackItems,
                  stack_min[currentPC],
                  stack_max[currentPC]);
            }
            // terminal op, reset currentMin and currentMax to forward set values
            if (nextPC < codeLength) {
              currentMax = stack_max[nextPC];
              currentMin = stack_min[nextPC];
            }
            break;
          case JumpFOperation.OPCODE:
            int jumpFTargetSectionNum = readBigEndianI16(currentPC + 1, code);
            workList.put(jumpFTargetSectionNum);
            CodeSection targetCs = eofLayout.getCodeSection(jumpFTargetSectionNum);
            if (currentMax + targetCs.getMaxStackHeight() - targetCs.getInputs()
                > MAX_STACK_HEIGHT) {
              return format(
                  "JUMPF at section %d pc %d would exceed maximum stack with %d items",
                  codeSectionToValidate,
                  currentPC,
                  currentMax + targetCs.getMaxStackHeight() - targetCs.getInputs());
            }
            if (targetCs.isReturning()) {
              if (currentMin != currentMax) {
                return format(
                    "JUMPF at section %d pc %d has a variable stack height %d/%d",
                    codeSectionToValidate, currentPC, currentMin, currentMax);
              }
              int expectedMax = toValidate.outputs + targetCs.inputs - targetCs.outputs;
              if (currentMax != expectedMax) {
                return format(
                    "%s JUMPF at section %d pc %d has incompatible stack height for returning section %d (%d != %d + %d - %d)",
                    currentMax < expectedMax ? "stack_underflow" : "stack_higher_than_outputs",
                    codeSectionToValidate,
                    currentPC,
                    jumpFTargetSectionNum,
                    currentMax,
                    toValidate.outputs,
                    targetCs.inputs,
                    targetCs.outputs);
              }
            } else {
              if (currentMin < targetCs.getInputs()) {
                return format(
                    "stack_underflow JUMPF at section %d pc %d has insufficient minimum stack height for non returning section %d (%d != %d)",
                    codeSectionToValidate,
                    currentPC,
                    jumpFTargetSectionNum,
                    currentMin,
                    targetCs.inputs);
              }
            }
            // fall through for terminal op handling
          case StopOperation.OPCODE,
              ReturnContractOperation.OPCODE,
              ReturnOperation.OPCODE,
              RevertOperation.OPCODE,
              InvalidOperation.OPCODE:
            // terminal op, reset currentMin and currentMax to forward set values
            if (nextPC < codeLength) {
              currentMax = stack_max[nextPC];
              currentMin = stack_min[nextPC];
            }
            break;
          default:
            // Ordinary operations, update stack for next operation
            if (nextPC < codeLength) {
              currentMax = max(stack_max[nextPC], currentMax);
              stack_max[nextPC] = currentMax;
              currentMin = min(stack_min[nextPC], currentMin);
              stack_min[nextPC] = min(stack_min[nextPC], currentMin);
            }
            break;
        }
        currentPC = nextPC;
      }

      if (maxStackHeight != toValidate.maxStackHeight) {
        return format(
            "invalid_max_stack_height Calculated (%d) != reported (%d)",
            maxStackHeight, toValidate.maxStackHeight);
      }
      if (unusedBytes != 0) {
        return format("Dead code detected in section %d", codeSectionToValidate);
      }

      return null;
    } catch (RuntimeException re) {
      re.printStackTrace();
      return "Internal Exception " + re.getMessage();
    }
  }
}
