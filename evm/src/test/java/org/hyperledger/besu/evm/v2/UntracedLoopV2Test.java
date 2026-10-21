/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.v2;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.testutils.ByteCodeBuilder;
import org.hyperledger.besu.evm.v2.ProgramRun.Fork;
import org.hyperledger.besu.evm.v2.ProgramRun.Outcome;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/**
 * Runs programs aimed at the operations the untraced EVM v2 loop runs as copies of their code, on
 * v1 and on v2 without a tracer, which takes the untraced loop, with the gas running out at those
 * operations, so that every copy is also the operation that runs out of gas or halts. The
 * differential tests of random programs run with plenty of gas and so seldom reach a fallback.
 */
class UntracedLoopV2Test {

  /** The forks at which an inline case changes, and the newest ones. */
  static final List<Fork> FORKS =
      ProgramRun.FORKS.stream()
          .filter(
              fork ->
                  Set.of(
                          "frontier",
                          "byzantium",
                          "constantinople",
                          "london",
                          "shanghai",
                          "cancun",
                          "osaka",
                          "amsterdam")
                      .contains(fork.name()))
          .toList();

  // PUSH1 1, PUSH1 0, SSTORE, PUSH1 0x2a, PUSH1 0, MSTORE, PUSH1 0x20, PUSH1 0, RETURN
  private static final Bytes CALLEE_CODE = Bytes.fromHexString("0x6001600055602a60005260206000f3");

  private static final List<Bytes32> SLOTS = List.of(Bytes32.ZERO);

  private static final long PLENTY = 10_000_000L;

  /** Programs aimed at the edges of the copied operations: stack limits, jumps, code end, gas. */
  private static final Map<String, String> EDGE_PROGRAMS =
      Map.ofEntries(
          Map.entry("dup16 underflow", "6001".repeat(15) + "8f"),
          Map.entry("dup16 exact", "6001".repeat(16) + "8f"),
          Map.entry("swap16 underflow", "6001".repeat(16) + "9f"),
          Map.entry("swap16 exact", "6001".repeat(17) + "9f"),
          Map.entry("pop empty", "50"),
          Map.entry("jump empty", "56"),
          Map.entry("jumpi one item", "600157"),
          Map.entry("push overflow", "6001".repeat(1025)),
          Map.entry("push2 overflow", "610102".repeat(1025)),
          Map.entry("dup overflow", "6001" + "80".repeat(1024)),
          Map.entry("push0 overflow", "5f".repeat(1025)),
          Map.entry("gas overflow", "5a".repeat(1025)),
          Map.entry(
              "stack growth",
              "6001".repeat(300) + "80".repeat(100) + "90".repeat(50) + "50".repeat(400)),
          Map.entry("jump not jumpdest", "600356"),
          Map.entry("jump past code", "60ff56"),
          Map.entry("jump into push data", "600456605b5b"),
          Map.entry("jump wide destination", "64000000000856005b"),
          Map.entry("jump 32 byte destination", "7f" + "00".repeat(31) + "2356" + "00" + "5b"),
          Map.entry("jump negative int", "638000000056"),
          Map.entry("jump backwards", "5b" + "6000" + "15" + "6000" + "57" + "600056"),
          Map.entry("jumpi zero condition invalid destination", "600060ff57"),
          Map.entry("jumpi zero condition wide", "7f" + "00".repeat(32) + "60ff5700"),
          Map.entry("jumpi taken", "6001600657005b"),
          Map.entry("jumpi invalid taken", "6001600557005b"),
          Map.entry("loop", "6000" + "5b" + "600101" + "80" + "6010" + "11" + "600257"),
          Map.entry("truncated push2", "6101"),
          Map.entry("truncated push8", "67010203"),
          Map.entry("truncated push32", "7f0102"),
          Map.entry("push at end", "6001"),
          Map.entry("push2 to push8 near the end", "61010262010203630102030464010203040500"),
          Map.entry("push2 to push8", "6101026201020363010203046401020304056501020304050600"),
          Map.entry("push0", "5f5f01"),
          Map.entry("implicit stop", "60016002"),
          Map.entry("call", "6000600060006000600062ca11ee61fffff1"),
          Map.entry("call then continue", "6000600060206000600062ca11ee61fffff15060005180"),
          Map.entry("return", "602a60005260206000f3"),
          Map.entry("revert", "602a60005260206000fd"),
          Map.entry("invalid", "fe"),
          Map.entry("jumpi on eq", "6001600114600957005b00"),
          Map.entry("jumpi on eq false", "6001600214600957005b00"),
          Map.entry("jumpi on 32 bytes", "7f" + "00".repeat(31) + "01" + "602557005b"),
          Map.entry("jumpi on 32 zero bytes", "7f" + "00".repeat(32) + "602557005b"),
          Map.entry("jumpi on callvalue", "3460065700005b"),
          Map.entry(
              "iszero kinds", "600115" + "7f" + "00".repeat(32) + "15" + "600160011415" + "3415"),
          Map.entry("jump push3 destination", "6200000656005b"),
          Map.entry("jump push4 destination", "630000000756005b"),
          Map.entry("jump computed destination", "600360040156005b"),
          Map.entry("eq across lengths", "6005" + "7f" + "00".repeat(31) + "05" + "14"),
          Map.entry("eq different", "6005" + "7f" + "01" + "00".repeat(30) + "05" + "14"),
          Map.entry("lt gt across lengths", "610100" + "60ff" + "10" + "610100" + "60ff" + "11"),
          Map.entry("lt gt equal", "6007600710" + "6007600711"),
          Map.entry("signed comparisons", "60ff60000b" + "6001" + "12" + "60ff60000b6001" + "13"),
          Map.entry(
              "signextend kinds", "60ff60000b" + "61ffff60010b" + "60ff601e0b" + "60ff60200b"),
          Map.entry("comparisons of zeros", "5f600014" + "6000" + "7f" + "00".repeat(32) + "10"),
          Map.entry("comparisons of eq results", "600160011460016002141460016001141011"),
          Map.entry("comparisons of callvalue", "3434143434103411"),
          Map.entry("jumpdest as last byte", "61000356" + "5b"),
          Map.entry(
              "dispatcher",
              "60003560e01c" // PUSH1 0, CALLDATALOAD, PUSH1 0xe0, SHR
                  + "80630102030414" // DUP1, PUSH4 0x01020304, EQ
                  + "61001d57" // PUSH2 29, JUMPI
                  + "80630a0b0c0d14" // DUP1, PUSH4 0x0a0b0c0d, EQ
                  + "61001f57" // PUSH2 31, JUMPI
                  + "00"
                  + "5b00"
                  + "5b600160005500"),
          Map.entry("unassigned", "0c"));

  @ParameterizedTest(name = "{0}")
  @FieldSource("FORKS")
  void edgeProgramsEndAsOnV1WhenTheGasRunsOut(final Fork fork) {
    final EVM v1 = fork.factory().apply(ProgramRun.V1);
    final EVM v2 = fork.factory().apply(ProgramRun.V2);
    for (final Map.Entry<String, String> program : EDGE_PROGRAMS.entrySet()) {
      final Bytes code = Bytes.fromHexString(program.getValue());
      final Outcome unlimited = run(v2, code, PLENTY);
      assertThat(unlimited).as(program.getKey()).isEqualTo(run(v1, code, PLENTY));
      final long used = PLENTY - unlimited.remainingGas();
      // every limit where the last operations run out, so that a halt and running out meet there
      final long everyFrom = Math.max(0, used - 128);
      final long stride = Math.max(1, everyFrom / 100);
      for (long gas = 0;
          gas <= used + 1;
          gas = gas < everyFrom ? Math.min(gas + stride, everyFrom) : gas + 1) {
        assertThat(run(v2, code, gas))
            .as("%s with %d gas", program.getKey(), gas)
            .isEqualTo(run(v1, code, gas));
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("FORKS")
  void everyCopiedOperationRunsOutOfGasAsOnV1(final Fork fork) {
    final EVM v1 = fork.factory().apply(ProgramRun.V1);
    final EVM v2 = fork.factory().apply(ProgramRun.V2);
    final Bytes code =
        everyCopiedOperation(
            atLeast(v2, EvmSpecVersion.SHANGHAI), atLeast(v2, EvmSpecVersion.CONSTANTINOPLE));
    final Outcome unlimited = run(v2, code, PLENTY);
    assertThat(unlimited.haltReason()).isEmpty();
    assertThat(unlimited).isEqualTo(run(v1, code, PLENTY));
    final long used = PLENTY - unlimited.remainingGas();
    for (long gas = 0; gas <= used; gas++) {
      assertThat(run(v2, code, gas)).as("%d gas", gas).isEqualTo(run(v1, code, gas));
      // the operations' classes, which the traced loop runs, halt where v1 halts too
      assertThat(runTraced(v2, code, gas))
          .as("traced, %d gas", gas)
          .isEqualTo(runTraced(v1, code, gas));
    }
  }

  @ParameterizedTest(name = "{0}")
  @FieldSource("FORKS")
  void randomProgramsWithLittleGasEndAsOnV1(final Fork fork) {
    final EVM v1 = fork.factory().apply(ProgramRun.V1);
    final EVM v2 = fork.factory().apply(ProgramRun.V2);
    final Random random = new Random(0x5eed + fork.name().hashCode());
    for (int i = 0; i < 500; i++) {
      final Bytes code = randomProgram(random);
      final long gas = random.nextInt(4) == 0 ? random.nextInt(200) : random.nextInt(60_000);
      assertThat(run(v2, code, gas))
          .as("program %s with %d gas", code.toHexString(), gas)
          .isEqualTo(run(v1, code, gas));
    }
  }

  private static Outcome run(final EVM evm, final Bytes code, final long gas) {
    return ProgramRun.run(evm, code, CALLEE_CODE, Bytes.EMPTY, gas, false, SLOTS);
  }

  private static Outcome runTraced(final EVM evm, final Bytes code, final long gas) {
    return ProgramRun.runTraced(evm, code, CALLEE_CODE, Bytes.EMPTY, gas, false, SLOTS);
  }

  /**
   * One use of every operation the loop runs as a copy, in both of its outcomes where it has two,
   * with a few operations of the general path in between.
   */
  private static Bytes everyCopiedOperation(final boolean push0, final boolean shl) {
    final ByteCodeBuilder a = new ByteCodeBuilder();
    a.hex("6001600210 50 6001600211 50 6001600212 50 6001600213 50"); // LT, GT, SLT, SGT
    a.hex("6005600514 15 50"); // EQ, ISZERO
    a.hex("60ff60000b 50 600160200b 50"); // SIGNEXTEND, of a byte and past the word
    a.hex("6002600301 50 6002600303 50 6003600516 50"); // ADD, SUB, AND
    a.hex(shl ? "600160041b 50 600160041c 50" : "58 50"); // SHL, SHR from Constantinople, else PC
    a.hex("6003600517 50 600519 50"); // OR, NOT
    a.hex("5a 50"); // GAS
    a.hex(push0 ? "5f 50" : "58 50"); // PUSH0 from Shanghai, else PC
    a.hex("610102 62010203 6301020304 640102030405 65010203040506 5050505050"); // PUSH2 to PUSH6
    a.hex("6601020304050607 670102030405060708 5050"); // PUSH7, PUSH8
    a.hex("7f" + "11".repeat(32) + "50"); // PUSH32, through the general path
    a.hex("6001 80 81 90 91 505050"); // DUP1, DUP2, SWAP1, SWAP2
    a.hex("602a600052"); // MSTORE, through the general path
    // JUMPI taken, with the destination through the stack
    a.hex("6001");
    a.push2(a.size() + 5).hex("57 00 5b");
    // JUMP, with the destination through the stack
    a.push2(a.size() + 5).hex("56 00 5b");
    // JUMPI not taken
    a.hex("6000 60ff 57");
    a.hex("00");
    return Bytes.fromHexString(a.toString());
  }

  private static Bytes randomProgram(final Random random) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final List<Integer> jumpTargets = new ArrayList<>();
    final List<Integer> jumpSites = new ArrayList<>();
    final int length = 1 + random.nextInt(120);
    for (int i = 0; i < length; i++) {
      final int kind = random.nextInt(100);
      if (kind < 25) {
        final int size =
            random.nextInt(8) == 0 ? 0 : 1 + random.nextInt(random.nextBoolean() ? 8 : 32);
        out.write(0x5f + size);
        for (int b = 0; b < size; b++) {
          out.write(random.nextInt(3) == 0 ? 0 : random.nextInt(256));
        }
      } else if (kind < 37) {
        out.write(0x80 + random.nextInt(random.nextBoolean() ? 3 : 16));
      } else if (kind < 45) {
        out.write(0x90 + random.nextInt(random.nextBoolean() ? 3 : 16));
      } else if (kind < 50) {
        out.write(0x50);
      } else if (kind < 56) {
        jumpTargets.add(out.size());
        out.write(0x5b);
      } else if (kind < 63) {
        // PUSH2 destination, patched below, then JUMP or JUMPI
        jumpSites.add(out.size());
        out.write(0x61);
        out.write(0);
        out.write(0);
        out.write(random.nextBoolean() ? 0x56 : 0x57);
      } else if (kind < 66) {
        out.write(random.nextBoolean() ? 0x56 : 0x57);
      } else if (kind < 80) {
        final int[] cheap = {
          0x01, 0x02, 0x03, 0x0b, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x19, 0x1b, 0x1c, 0x1d,
          0x51, 0x52, 0x58, 0x59, 0x5a, 0x35, 0x36
        };
        out.write(cheap[random.nextInt(cheap.length)]);
      } else if (kind < 86) {
        // a small operand first, so that SIGNEXTEND and the comparisons see in-range arguments
        out.write(0x60);
        out.write(random.nextInt(random.nextBoolean() ? 40 : 300) & 0xff);
        final int[] small = {0x0b, 0x10, 0x11, 0x12, 0x13, 0x14};
        out.write(small[random.nextInt(small.length)]);
      } else if (kind < 92) {
        final int[] environment = {0x30, 0x32, 0x33, 0x34, 0x36, 0x38, 0x3d, 0x43, 0x47, 0x58};
        out.write(environment[random.nextInt(environment.length)]);
      } else {
        out.write(random.nextInt(256));
      }
    }
    final byte[] code = out.toByteArray();
    for (final int site : jumpSites) {
      final int destination =
          jumpTargets.isEmpty() || random.nextInt(8) == 0
              ? random.nextInt(code.length + 2)
              : jumpTargets.get(random.nextInt(jumpTargets.size()));
      code[site + 1] = (byte) (destination >> 8);
      code[site + 2] = (byte) destination;
    }
    return Bytes.wrap(code);
  }

  private static boolean atLeast(final EVM evm, final EvmSpecVersion version) {
    return evm.getEvmVersion().ordinal() >= version.ordinal();
  }
}
