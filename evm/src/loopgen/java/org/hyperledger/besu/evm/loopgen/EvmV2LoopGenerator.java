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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.source.tree.CaseTree;
import com.sun.source.util.TreePath;

/**
 * Writes, or checks, the copies of the most frequent EVM v2 operations in the untraced loop of
 * EVM.java ({@code runToHaltV2Untraced}).
 *
 * <p><b>Why copies.</b> C2 keeps the loop's stack top, pc and gas in registers only while the loop
 * calls nothing on the way from one operation to the next, and it gives up inlining once the loop
 * and what it inlines reach 8000 bytes of bytecode. The loop therefore runs the most frequent
 * operations as copies of their code, which work on its locals instead of the frame, and sends
 * every other operation to {@code executeOperationV2}. The copies are made from the operations' own
 * code, so that each operation has one implementation, and every build checks that EVM.java holds
 * them as this generator would write them.
 *
 * <p><b>How.</b>
 *
 * <ol>
 *   <li>{@link JavaSources} has javac read EVM.java and the operation classes, so that every name
 *       in them is resolved to what it refers to.
 *   <li>For each opcode {@link CopiedOperations} lists, {@link CaseCopier} copies what {@code
 *       executeOperationV2}'s switch runs for it. It copies the code as it is, except for the
 *       substitutions {@link UntracedLoop} lists, and rejects whatever it cannot copy that way.
 *   <li>{@link GeneratedRegions} writes the copies, and the table the loop dispatches with, between
 *       the generated markers of EVM.java.
 * </ol>
 *
 * <p><b>Example.</b> POP runs {@code PopOperationV2.staticOperation}:
 *
 * <pre>{@code
 * if (!frame.stackHasItemsV2(1)) {
 *   return UNDERFLOW_RESPONSE;
 * }
 * frame.setTopV2(frame.stackTopV2() - 1);
 * return popSuccess; // new OperationResult(2, null)
 * }</pre>
 *
 * which the loop holds as
 *
 * <pre>{@code
 * case COPIED_POP -> {
 *   if (!((spBase + 384) >= 1)) {
 *     frame.setTopV2(sp);
 *     frame.setPC(pc);
 *     frame.setGasRemaining(gas);
 *     result = AbstractOperationV2.UNDERFLOW_RESPONSE;
 *     break;
 *   }
 *   sp = (spBase + 384) - 1;
 *   cost = 2L;
 *   step = 1;
 * }
 * }</pre>
 *
 * The frame's stack became the loop's {@code sp}, read as {@code spBase} plus the case's constant.
 * The success it returned became its gas cost and pc step. The halt it returned hands the loop's
 * state to the frame and leaves the result for the loop to complete, as the general path does.
 *
 * <p>{@code EvmV2LoopGeneratorTest} runs the generator on a smaller example, which shows each rule:
 * {@code src/test/resources/loopgen-example} holds its input and the EVM.java written from it.
 */
public final class EvmV2LoopGenerator {

  private static final String EVM_JAVA = "org/hyperledger/besu/evm/EVM.java";

  private EvmV2LoopGenerator() {}

  /**
   * Writes the copies into EVM.java, or checks that it holds them.
   *
   * @param args {@code write} or {@code check}, the main source directory, the class path the main
   *     sources compile against and, for {@code check}, a file to write once the check passed
   * @throws IOException if a file cannot be read or written
   */
  public static void main(final String[] args) throws IOException {
    if (args.length < 3 || !(args[0].equals("write") || args[0].equals("check"))) {
      System.err.println(
          "usage: EvmV2LoopGenerator write|check <src/main/java> <class path> [<marker file>]");
      System.exit(2);
    }
    final boolean write = args[0].equals("write");
    final Path sourceRoot = Path.of(args[1]);
    final Path evmFile = sourceRoot.resolve(EVM_JAVA);
    final String current = Files.readString(evmFile, UTF_8);

    final String generated;
    try {
      generated = generate(sourceRoot, args[2], current, CopiedOperations.ALL);
    } catch (final GeneratorException e) {
      System.err.println("The EVM v2 loop generator stopped: " + e.getMessage());
      System.exit(1);
      return;
    }

    if (write) {
      Files.writeString(evmFile, generated, UTF_8);
      System.out.println("Wrote " + CopiedOperations.ALL.size() + " cases into " + evmFile);
      return;
    }
    final String difference = firstDifferenceIgnoringWhitespace(generated, current);
    if (difference != null) {
      System.err.println(
          evmFile
              + " does not hold the copies of the operations' code. Run ./gradlew"
              + " :evm:generateEvmV2Loop after changing an operation the untraced loop copies. "
              + difference);
      System.exit(1);
    }
    if (args.length > 3) {
      final Path marker = Path.of(args[3]);
      Files.createDirectories(marker.getParent());
      Files.writeString(marker, "checked " + CopiedOperations.ALL.size() + " cases\n", UTF_8);
    }
  }

  /**
   * EVM.java's text with the generated regions replaced by fresh copies.
   *
   * @param sourceRoot the directory with EVM.java and the operations under their packages
   * @param classPath the class path they compile against
   * @param current EVM.java's current text
   * @param operations the operations to copy, in the order of the loop's switch
   * @return the new text
   * @throws IOException if a source cannot be read
   */
  static String generate(
      final Path sourceRoot,
      final String classPath,
      final String current,
      final List<CopiedOperations.Operation> operations)
      throws IOException {
    try (JavaSources sources = JavaSources.read(sourceRoot, EVM_JAVA, classPath)) {
      final Map<Integer, TreePath> generalPathCases = sources.executeOperationV2Cases();
      final CopiedDeclarations declarations = new CopiedDeclarations(sources);
      final List<GeneratedRegions.CopiedCase> cases = new ArrayList<>();
      int index = 1;
      for (final CopiedOperations.Operation operation : operations) {
        final TreePath generalPathCase = generalPathCases.get(operation.opcode());
        if (generalPathCase == null) {
          throw new GeneratorException(
              "executeOperationV2 has no case for opcode 0x"
                  + Integer.toHexString(operation.opcode()));
        }
        final CaseCopier copier = new CaseCopier(sources, declarations, index);
        cases.add(
            new GeneratedRegions.CopiedCase(
                operation.name(),
                index,
                JavaSources.opcodesOf((CaseTree) generalPathCase.getLeaf()),
                CaseCopier.describe(generalPathCase),
                copier.copy(generalPathCase)));
        index++;
      }
      return GeneratedRegions.replace(current, cases, declarations.text());
    }
  }

  /** Where two texts first differ once whitespace, which the formatter decides, is ignored. */
  static String firstDifferenceIgnoringWhitespace(final String expected, final String actual) {
    final String a = expected.replaceAll("\\s", "");
    final String b = actual.replaceAll("\\s", "");
    if (a.equals(b)) {
      return null;
    }
    int at = 0;
    while (at < Math.min(a.length(), b.length()) && a.charAt(at) == b.charAt(at)) {
      at++;
    }
    return "First difference, without whitespace: expected "
        + a.substring(at, Math.min(a.length(), at + 80))
        + " but found "
        + b.substring(at, Math.min(b.length(), at + 80));
  }
}
