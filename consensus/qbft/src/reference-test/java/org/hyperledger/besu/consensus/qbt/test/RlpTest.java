/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.consensus.qbt.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.qbt.support.RlpTestCaseSpec;
import org.hyperledger.besu.consensus.qbt.support.RlpTestInput;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class RlpTest {

  private static final Logger LOG = LogManager.getLogger();

  @Test
  public void messagesCanBeRLPEncodedAndDecoded() throws IOException, URISyntaxException {
    final ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());

    final Path rlpPath = Path.of(RlpTest.class.getResource("/rlp").toURI());
    try (DirectoryStream<Path> rlpFiles = Files.newDirectoryStream(rlpPath)) {
      rlpFiles.forEach(
          rlpTestFile -> {
            try {
              List<RlpTestCaseSpec> testCases =
                  mapper.readValue(rlpTestFile.toFile(), new TypeReference<>() {});
              for (RlpTestCaseSpec testCase : testCases) {
                // message -> RLP
                assertThat(Bytes.fromHexStringLenient(testCase.getOutput()))
                    .isEqualTo(testCase.getInput().toRlp());

                // RLP -> message
                final RlpTestInput rlpTestInput =
                    testCase.getInput().fromRlp(Bytes.fromHexString(testCase.getOutput()));
                assertThat(testCase.getInput()).usingRecursiveComparison().isEqualTo(rlpTestInput);
              }
            } catch (IOException e) {
              LOG.error("Unable to read rlp test file", e);
              throw new IllegalStateException(e);
            }
          });
    }
  }
}
