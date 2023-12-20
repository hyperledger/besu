/*
 * Copyright Hyperledger Besu contributors.
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

package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionMetadataTest {
  @TempDir public Path temporaryFolder;

  @Test
  void getVersion() {
    final VersionMetadata versionMetadata = new VersionMetadata("23.10.2");
    assertThat(versionMetadata).isNotNull();
    assertThat(versionMetadata.getBesuVersion()).isEqualTo("23.10.2");
  }

  @Test
  void metaFileShouldContain() throws Exception {
    final Path tempDataDir =
        createAndWrite("data", "VERSION_METADATA.json", "{\"besuVersion\":\"23.10.3\"}");

    final VersionMetadata versionMetadata = VersionMetadata.lookUpFrom(tempDataDir);
    assertThat(versionMetadata).isNotNull();
    assertThat(versionMetadata.getBesuVersion()).isEqualTo("23.10.3");
  }

  private Path createAndWrite(final String dir, final String file, final String content)
      throws IOException {
    return createAndWrite(temporaryFolder, dir, file, content);
  }

  private Path createAndWrite(
      final Path temporaryFolder, final String dir, final String file, final String content)
      throws IOException {
    final Path tmpDir = temporaryFolder.resolve(dir);
    Files.createDirectories(tmpDir);
    createAndWrite(tmpDir.resolve(file), content);
    return tmpDir;
  }

  private void createAndWrite(final Path path, final String content) throws IOException {
    path.toFile().createNewFile();
    Files.writeString(path, content);
  }
}
