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
package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec.TestResult.failed;
import static org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec.TestResult.passed;
import static org.hyperledger.besu.evmtool.EOFTestSubCommand.COMMAND_NAME;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec;
import org.hyperledger.besu.ethereum.referencetests.EOFTestCaseSpec.TestResult;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV1;
import org.hyperledger.besu.evm.code.CodeV1Validation;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.util.LogConfigurator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;

@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Runs EOF validation reference tests",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class EOFTestSubCommand implements Runnable {
  public static final String COMMAND_NAME = "eof-test";
  @CommandLine.ParentCommand private final EvmToolCommand parentCommand;

  // picocli does it magically
  @CommandLine.Parameters private final List<Path> eofTestFiles = new ArrayList<>();

  @CommandLine.Option(
      names = {"--fork-name"},
      description = "Limit execution to one fork.")
  private String forkName = null;

  @CommandLine.Option(
      names = {"--test-name"},
      description = "Limit execution to one test.")
  private String testVectorName = null;

  public EOFTestSubCommand() {
    this(null);
  }

  public EOFTestSubCommand(final EvmToolCommand parentCommand) {
    this.parentCommand = parentCommand;
  }

  @Override
  public void run() {
    LogConfigurator.setLevel("", "OFF");
    // presume ethereum mainnet for reference and EOF tests
    SignatureAlgorithmFactory.setDefaultInstance();
    final ObjectMapper eofTestMapper = JsonUtils.createObjectMapper();

    final JavaType javaType =
        eofTestMapper
            .getTypeFactory()
            .constructParametricType(Map.class, String.class, EOFTestCaseSpec.class);
    try {
      if (eofTestFiles.isEmpty()) {
        // if no EOF tests were specified use standard input to get filenames
        final BufferedReader in =
            new BufferedReader(new InputStreamReader(parentCommand.in, UTF_8));
        while (true) {
          final String fileName = in.readLine();
          if (fileName == null) {
            // reached end of file.  Stop the loop.
            break;
          }
          final File file = new File(fileName);
          if (file.isFile()) {
            final Map<String, EOFTestCaseSpec> eofTests = eofTestMapper.readValue(file, javaType);
            executeEOFTest(file.toString(), eofTests);
          } else {
            parentCommand.out.println("File not found: " + fileName);
          }
        }
      } else {
        for (final Path eofTestFile : eofTestFiles) {
          final Map<String, EOFTestCaseSpec> eofTests;
          if ("stdin".equals(eofTestFile.toString())) {
            eofTests = eofTestMapper.readValue(parentCommand.in, javaType);
          } else {
            eofTests = eofTestMapper.readValue(eofTestFile.toFile(), javaType);
          }
          executeEOFTest(eofTestFile.toString(), eofTests);
        }
      }
    } catch (final JsonProcessingException jpe) {
      parentCommand.out.println("File content error: " + jpe);
    } catch (final IOException e) {
      System.err.println("Unable to read EOF test file");
      e.printStackTrace(System.err);
    }
  }

  record TestExecutionResult(
      String fileName,
      String group,
      String name,
      String fork,
      boolean pass,
      String expectedError,
      String actualError) {}

  private void executeEOFTest(final String fileName, final Map<String, EOFTestCaseSpec> eofTests) {
    List<TestExecutionResult> results = new ArrayList<>();

    for (var testGroup : eofTests.entrySet()) {
      String groupName = testGroup.getKey();
      for (var testVector : testGroup.getValue().getVector().entrySet()) {
        String testName = testVector.getKey();
        if (testVectorName != null && !testVectorName.equals(testName)) {
          continue;
        }
        String code = testVector.getValue().code();
        for (var testResult : testVector.getValue().results().entrySet()) {
          String expectedForkName = testResult.getKey();
          if (forkName != null && !forkName.equals(expectedForkName)) {
            continue;
          }
          TestResult expectedResult = testResult.getValue();
          EvmSpecVersion evmVersion = EvmSpecVersion.fromName(expectedForkName);
          if (evmVersion == null) {
            results.add(
                new TestExecutionResult(
                    fileName,
                    groupName,
                    testName,
                    expectedForkName,
                    false,
                    "Valid fork name",
                    "Unknown fork: " + expectedForkName));

            continue;
          }
          TestResult actualResult;
          if (evmVersion.ordinal() < EvmSpecVersion.PRAGUE_EOF.ordinal()) {
            actualResult = failed("EOF_InvalidCode");
          } else {
            actualResult = considerCode(code);
          }
          results.add(
              new TestExecutionResult(
                  fileName,
                  groupName,
                  testName,
                  expectedForkName,
                  actualResult.result() == expectedResult.result(),
                  expectedResult.exception(),
                  actualResult.exception()));
        }
      }
    }
    for (TestExecutionResult result : results) {
      try {
        parentCommand.out.println(JsonUtils.createObjectMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        e.printStackTrace(parentCommand.out);
        throw new RuntimeException(e);
      }
    }
  }

  public TestResult considerCode(final String hexCode) {
    Bytes codeBytes;
    try {
      codeBytes =
          Bytes.fromHexString(
              hexCode.replaceAll("(^|\n)#[^\n]*($|\n)", "").replaceAll("[^0-9A-Za-z]", ""));
    } catch (RuntimeException re) {
      return failed(re.getMessage());
    }
    if (codeBytes.isEmpty()) {
      return passed();
    }

    var layout = EOFLayout.parseEOF(codeBytes);
    if (!layout.isValid()) {
      return failed("layout - " + layout.invalidReason());
    }

    var code = CodeFactory.createCode(codeBytes, 1);
    if (!code.isValid()) {
      return failed("validate " + ((CodeInvalid) code).getInvalidReason());
    }
    if (code instanceof CodeV1 codeV1) {
      var result = CodeV1Validation.validate(codeV1.getEofLayout());
      if (result != null) {
        return (failed("deep validate error: " + result));
      }
    }

    return passed();
  }
}
