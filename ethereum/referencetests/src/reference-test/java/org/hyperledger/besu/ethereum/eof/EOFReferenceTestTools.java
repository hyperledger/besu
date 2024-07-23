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
import java.util.Map.Entry;

import org.apache.tuweni.bytes.Bytes;

import org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec;
import org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec.TestResult;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV1;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode;
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
                if (eofSpec.getVector() == null) {
                  return;
                }
                final Path path = Path.of(fullPath).getParent().getFileName();
                final String prefix = path + "/" + testName + "-";
                for (final Map.Entry<String, EOFTestCaseSpec.TestVector> entry :
                    eofSpec.getVector().entrySet()) {
                  final String name = entry.getKey();
                  final Bytes code = Bytes.fromHexString(entry.getValue().code());
                  final String containerKind = entry.getValue().containerKind();
                  for (final Entry<String, TestResult> result :
                      entry.getValue().results().entrySet()) {
                    final String eip = result.getKey();
                    final boolean runTest = EIPS_TO_RUN.contains(eip);
                    collector.add(
                        prefix + eip + '[' + name + ']',
                        fullPath,
                        eip,
                        code,
                        containerKind,
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

    // embedded containers rules changed
    params.ignore("efValidation/EOF1_embedded_container-Prague\\[EOF1_embedded_container_\\d+\\]");

    // truncated data is only allowed in embedded containers
    params.ignore("ori/validInvalid-Prague\\[validInvalid_48\\]");
    params.ignore("efExample/validInvalid-Prague\\[validInvalid_1\\]");
    params.ignore("efValidation/EOF1_truncated_section-Prague\\[EOF1_truncated_section_3\\]");
    params.ignore("efValidation/EOF1_truncated_section-Prague\\[EOF1_truncated_section_4\\]");
    params.ignore("EIP3540/validInvalid-Prague\\[validInvalid_2\\]");
    params.ignore("EIP3540/validInvalid-Prague\\[validInvalid_3\\]");

    // Orphan containers are no longer allowed
    params.ignore("efValidation/EOF1_returncontract_valid-Prague\\[EOF1_returncontract_valid_1\\]");
    params.ignore("efValidation/EOF1_returncontract_valid-Prague\\[EOF1_returncontract_valid_2\\]");
    params.ignore("efValidation/EOF1_eofcreate_valid-Prague\\[EOF1_eofcreate_valid_1\\]");
    params.ignore("efValidation/EOF1_eofcreate_valid-Prague\\[EOF1_eofcreate_valid_2\\]");
    params.ignore("efValidation/EOF1_section_order-Prague\\[EOF1_section_order_6\\]");
  }

  private EOFReferenceTestTools() {
    // utility class
  }

  //
  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  @SuppressWarnings("java:S5960") // This is not production code, this is testing code.
  public static void executeTest(
      final String fork,
      final Bytes code,
      final String containerKind,
      final EOFTestCaseSpec.TestResult expected) {
    EVM evm = ReferenceTestProtocolSchedules.create().geSpecByName(fork).getEvm();
    assertThat(evm).isNotNull();

    // hardwire in the magic byte transaction checks
    if (evm.getMaxEOFVersion() < 1) {
      assertThat(expected.exception()).isEqualTo("EOF_InvalidCode");
    } else {
      EOFLayout layout = EOFLayout.parseEOF(code);

      if (layout.isValid()) {
        Code parsedCode;
        if ("INITCODE".equals(containerKind)) {
          parsedCode = evm.getCodeForCreation(code);
        } else {
          parsedCode = evm.getCodeUncached(code);
        }
        if ("EOF_IncompatibleContainerKind".equals(expected.exception()) && parsedCode.isValid()) {
          EOFContainerMode expectedMode =
              EOFContainerMode.valueOf(containerKind == null ? "RUNTIME" : containerKind);
          EOFContainerMode containerMode =
              ((CodeV1) parsedCode).getEofLayout().containerMode().get();
          EOFContainerMode actualMode =
              containerMode == null ? EOFContainerMode.RUNTIME : containerMode;
          assertThat(actualMode)
              .withFailMessage("Code did not parse to valid containerKind of " + expectedMode)
              .isNotEqualTo(expectedMode);
        } else {
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

          if (expected.result()) {
            assertThat(code)
                .withFailMessage("Container round trip failed")
                .isEqualTo(layout.writeContainer(null));
          }
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
