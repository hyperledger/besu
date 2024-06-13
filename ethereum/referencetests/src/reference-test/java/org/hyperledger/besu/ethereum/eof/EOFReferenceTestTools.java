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
package org.hyperledger.besu.ethereum.eof;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV1;
import org.hyperledger.besu.evm.code.CodeV1Validation;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.testutil.JsonTestParameters;

public class EOFReferenceTestTools {
  private static final List<String> EIPS_TO_RUN;

  static {
    final String eips =
        System.getProperty("test.ethereum.eof.eips", "Prague,Osaka,Amsterdam,Bogota,Polis,Bangkok");
    EIPS_TO_RUN = Arrays.asList(eips.split(","));
  }

  private static final JsonTestParameters<?, ?> params =
      JsonTestParameters.create(EOFTestCaseSpec.class, EOFTestCaseSpec.TestResult.class)
          .generator(
              (testName, fullPath, eofSpec, collector) -> {
                final Path path = Path.of(fullPath).getParent().getFileName();
                final String prefix = path + "/" + testName + "-";
                for (final Map.Entry<String, EOFTestCaseSpec.TestVector> entry :
                    eofSpec.getVector().entrySet()) {
                  final String name = entry.getKey();
                  final Bytes code = Bytes.fromHexString(entry.getValue().code());
                  for (final var result : entry.getValue().results().entrySet()) {
                    final String eip = result.getKey();
                    final boolean runTest = EIPS_TO_RUN.contains(eip);
                    collector.add(
                        prefix + eip + '[' + name + ']',
                        fullPath,
                        eip,
                        code,
                        result.getValue(),
                        runTest);
                  }
                }
              });

  static {
    if (EIPS_TO_RUN.isEmpty()) {
      params.ignoreAll();
    }

    // TXCREATE still in tests, but has been removed
    params.ignore("EOF1_undefined_opcodes_186");
  }

  private EOFReferenceTestTools() {
    // utility class
  }

  //
  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  public static void executeTest(
      final String fork, final Bytes code, final EOFTestCaseSpec.TestResult expected) {
    EvmSpecVersion evmVersion = EvmSpecVersion.fromName(fork);
    assertThat(evmVersion).isNotNull();

    // hardwire in the magic byte transaction checks
    if (evmVersion.getMaxEofVersion() < 1) {
      assertThat(expected.exception()).isEqualTo("EOF_InvalidCode");
    } else {
      EOFLayout layout = EOFLayout.parseEOF(code);

      if (layout.isValid()) {
        Code parsedCode = CodeFactory.createCode(code, evmVersion.getMaxEofVersion());
        assertThat(parsedCode.isValid())
            .withFailMessage(
                () ->
                    EOFLayout.parseEOF(code).prettyPrint()
                        + "\nExpected exception :"
                        + expected.exception()
                        + " actual exception :"
                        + (parsedCode.isValid()
                            ? null
                            : ((CodeInvalid) parsedCode).getInvalidReason()))
            .isEqualTo(expected.result());
        if (parsedCode instanceof CodeV1 codeV1) {
          var deepValidate = CodeV1Validation.validate(codeV1.getEofLayout());
          assertThat(deepValidate)
              .withFailMessage(
                  () ->
                      codeV1.prettyPrint()
                          + "\nExpected exception :"
                          + expected.exception()
                          + " actual exception :"
                          + (parsedCode.isValid() ? null : deepValidate))
              .isNull();
        }

        if (expected.result()) {
          System.out.println(code);
          System.out.println(layout.writeContainer(null));
          assertThat(code)
              .withFailMessage("Container round trip failed")
              .isEqualTo(layout.writeContainer(null));
        }
      } else {
        assertThat(layout.isValid())
            .withFailMessage(
                () ->
                    "Expected exception - "
                        + expected.exception()
                        + " actual exception - "
                        + (layout.isValid() ? null : layout.invalidReason()))
            .isEqualTo(expected.result());
      }
    }
  }
}
