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
package org.hyperledger.besu.evm.loopgen;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The two regions of EVM.java the generator writes, each between a BEGIN and an END marker:
 *
 * <ul>
 *   <li>the case table, among EVM's members: a constant per case, the table the loop dispatches
 *       with, and the declarations {@link CopiedDeclarations} copied;
 *   <li>the cases, inside the switch of {@code runToHaltV2Untraced}.
 * </ul>
 *
 * Everything outside the markers is written by hand. The text is written unformatted; the build
 * formats it, and the check ignores whitespace.
 */
final class GeneratedRegions {

  static final String TABLE_BEGIN = "// BEGIN GENERATED case table";
  static final String TABLE_END = "// END GENERATED case table";
  static final String CASES_BEGIN = "// BEGIN GENERATED cases";
  static final String CASES_END = "// END GENERATED cases";

  /**
   * One case of the loop's switch.
   *
   * @param name its constant's name, after {@code COPIED_}
   * @param index its constant's value, from 1
   * @param opcodes the opcodes it runs
   * @param copiedFrom what it is a copy of, for the comments around it
   * @param code its statements
   */
  record CopiedCase(String name, int index, List<Integer> opcodes, String copiedFrom, String code) {

    String constant() {
      return "COPIED_" + name;
    }
  }

  private GeneratedRegions() {}

  /**
   * EVM.java's text with both regions replaced.
   *
   * @param evm EVM.java's current text
   * @param cases the cases, in the order of the switch
   * @param copiedDeclarations the declarations copied into EVM
   * @return the new text
   */
  static String replace(
      final String evm, final List<CopiedCase> cases, final String copiedDeclarations) {
    final String withTable =
        replaceRegion(evm, TABLE_BEGIN, TABLE_END, caseTable(cases, copiedDeclarations), 2);
    return replaceRegion(withTable, CASES_BEGIN, CASES_END, switchCases(cases), 8);
  }

  private static String caseTable(final List<CopiedCase> cases, final String copiedDeclarations) {
    final StringBuilder table = new StringBuilder();
    table.append(
        TABLE_BEGIN
            + " for the copies of the operations' code in the untraced loop\n"
            + "// below; do not edit, run ./gradlew :evm:generateEvmV2Loop\n");
    for (final CopiedCase c : cases) {
      table.append("private static final int %s = %d;\n".formatted(c.constant(), c.index()));
    }
    table.append(
        """

        // Per opcode: its case in the low byte, and above it the distance of the case's
        // constant from the stack top and pc that its copied code reads
        private static final int[] DISPATCH = new int[256];

        static {
        """);
    for (final CopiedCase c : cases) {
      for (final int[] range : consecutiveRanges(c.opcodes())) {
        table.append(
            "dispatch(%s, %s, %s);\n".formatted(c.constant(), hex(range[0]), hex(range[1])));
      }
    }
    table.append(
        """
        }

        private static void dispatch(final int copiedCase, final int first, final int last) {
        for (int op = first; op <= last; op++) {
        DISPATCH[op] = ((copiedCase * %d) << 8) | copiedCase;
        }
        }
        """
            .formatted(UntracedLoop.CASE_SPACING));
    table.append(copiedDeclarations);
    table.append(TABLE_END);
    return table.toString();
  }

  private static String switchCases(final List<CopiedCase> cases) {
    final StringBuilder switchCases = new StringBuilder();
    switchCases.append(
        CASES_BEGIN
            + ": copies of the operations' code;\n"
            + "// edit those, then run ./gradlew :evm:generateEvmV2Loop\n");
    for (final CopiedCase c : cases) {
      switchCases.append(
          """
          // BEGIN copied from %s
          case %s -> {
          %s
          }
          // END copied from %s
          """
              .formatted(c.copiedFrom(), c.constant(), c.code(), c.copiedFrom()));
    }
    switchCases.append(CASES_END);
    return switchCases.toString();
  }

  /** The opcodes as ranges of consecutive opcodes, from the lowest. */
  private static List<int[]> consecutiveRanges(final List<Integer> opcodes) {
    final List<int[]> ranges = new ArrayList<>();
    for (final int opcode : new TreeSet<>(opcodes)) {
      if (!ranges.isEmpty() && ranges.getLast()[1] == opcode - 1) {
        ranges.getLast()[1] = opcode;
      } else {
        ranges.add(new int[] {opcode, opcode});
      }
    }
    return ranges;
  }

  private static String hex(final int value) {
    return String.format("0x%02x", value);
  }

  /** The text with the lines from the begin marker's to the end marker's replaced. */
  private static String replaceRegion(
      final String text,
      final String begin,
      final String end,
      final String replacement,
      final int indent) {
    final int from = text.indexOf(begin);
    final int to = text.indexOf(end);
    if (from < 0 || to < from) {
      throw new GeneratorException("EVM.java lacks the markers " + begin + " ... " + end);
    }
    final int lineStart = text.lastIndexOf('\n', from) + 1;
    final int lineEnd = text.indexOf('\n', to);
    final StringBuilder indented = new StringBuilder();
    for (final String line : replacement.split("\n", -1)) {
      indented.append(line.isBlank() ? "" : " ".repeat(indent) + line.strip()).append('\n');
    }
    return text.substring(0, lineStart) + indented + text.substring(lineEnd + 1);
  }
}
