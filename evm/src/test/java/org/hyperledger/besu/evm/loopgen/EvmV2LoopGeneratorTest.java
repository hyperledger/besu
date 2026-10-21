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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Runs the loop generator on the example in {@code src/test/resources/loopgen-example}. {@code
 * input} holds a small EVM.java and one operation class, ExampleOperationV2, whose methods each use
 * what one rule of the generator handles; {@code expected/EVM.java} is the EVM.java the generator
 * writes from them, so each copy can be read next to the code it comes from.
 */
class EvmV2LoopGeneratorTest {

  private static final Path EXAMPLE = Path.of("src/test/resources/loopgen-example");
  private static final Path INPUT = EXAMPLE.resolve("input");
  private static final Path EXPECTED = EXAMPLE.resolve("expected/EVM.java");
  private static final Path WRITTEN = Path.of("build/loopgen-example/EVM.java").toAbsolutePath();

  @Test
  void copiesTheExampleOperations() throws IOException {
    final String generated =
        generate(
            List.of(
                new CopiedOperations.Operation("POP", 0x50),
                new CopiedOperations.Operation("PUSH1", 0x60),
                new CopiedOperations.Operation("JUMP", 0x56),
                new CopiedOperations.Operation("PUSH0", 0x5f)));

    final String difference =
        EvmV2LoopGenerator.firstDifferenceIgnoringWhitespace(
            Files.readString(EXPECTED, UTF_8), generated);
    if (difference != null) {
      Files.createDirectories(WRITTEN.getParent());
      Files.writeString(WRITTEN, generated, UTF_8);
    }
    assertThat(difference)
        .as("the generator wrote %s, which differs from %s", WRITTEN, EXPECTED)
        .isNull();
  }

  @Test
  void rejectsAUseOfTheFrameTheLoopHasNoLocalFor() {
    assertThatThrownBy(() -> generate(List.of(new CopiedOperations.Operation("STORAGE", 0x54))))
        .isInstanceOf(GeneratorException.class)
        .hasMessage("ExampleOperationV2.java:85: cannot copy the frame's getWorldUpdater()");
  }

  @Test
  void rejectsAnOpcodeTheGeneralPathHasNoCaseFor() {
    assertThatThrownBy(() -> generate(List.of(new CopiedOperations.Operation("MISSING", 0x99))))
        .isInstanceOf(GeneratorException.class)
        .hasMessage("executeOperationV2 has no case for opcode 0x99");
  }

  private static String generate(final List<CopiedOperations.Operation> operations)
      throws IOException {
    return EvmV2LoopGenerator.generate(
        INPUT,
        System.getProperty("java.class.path"),
        Files.readString(INPUT.resolve("org/hyperledger/besu/evm/EVM.java"), UTF_8),
        operations);
  }
}
