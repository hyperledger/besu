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
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;

import java.io.File;

import org.junit.Test;

public class ExportPublicKeySubCommandTest extends CommandTestAbstract {

  @Test
  public void callingExportPublicKeySubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand("export-pub-key");
    final String expectedErrorOutputStart = "Missing required parameter: PATH";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingExportPublicKeySubCommandHelpMustDisplayImportUsage() {
    parseCommand("export-pub-key", "--help");
    final String expectedOutputStart = "Usage: pantheon export-pub-key [-hV] PATH";
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingExportPublicKeySubCommandWithFilePathMustWritePublicKeyInThisFile()
      throws Exception {

    final KeyPair keyPair = KeyPair.generate();

    when(mockController.getLocalNodeKeyPair()).thenReturn(keyPair);

    final File file = File.createTempFile("public", "key");
    parseCommand("export-pub-key", file.getPath());

    assertThat(contentOf(file))
        .startsWith(keyPair.getPublicKey().toString())
        .endsWith(keyPair.getPublicKey().toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
