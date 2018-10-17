/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ImportSubCommandTest extends CommandTestAbstract {

  @Test
  public void callingImportSubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand("import");
    final String expectedErrorOutputStart = "Missing required parameter: PATH";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingImportSubCommandHelpMustDisplayImportUsage() {
    parseCommand("import", "--help");
    final String expectedOutputStart = "Usage: pantheon import [-hV] PATH";
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingImportSubCommandWithPathMustImportBlocksWithThisPath() throws Exception {
    final Path path = Paths.get(".");
    parseCommand("import", path.toString());

    verify(mockBlockImporter).importBlockchain(pathArgumentCaptor.capture(), any());

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(path);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
