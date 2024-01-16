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

package org.hyperledger.besu.plugin.services.storage.rocksdb.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseMetadataTest {
  @TempDir public Path temporaryFolder;

  @Test
  void readingMetadataV1() throws Exception {
    final Path tempDataDir =
        createAndWrite(
            "data", "DATABASE_METADATA.json", "{\"version\":2 , \"privacyVersion\":1}");

    final DatabaseMetadata databaseMetadata = DatabaseMetadata.lookUpFrom(tempDataDir);
    assertThat(databaseMetadata.getFormat()).isEqualTo(DataStorageFormat.BONSAI);
    assertThat(databaseMetadata.maybePrivacyVersion()).isNotEmpty();
    assertThat(databaseMetadata.maybePrivacyVersion().getAsInt()).isEqualTo(1);
  }

  @Test
  void metaFileShouldBeSoughtIntoDataDirFirst() throws Exception {
    final Path tempDataDir = createAndWrite("data", "DATABASE_METADATA.json", "{\"version\":42}");
    final DatabaseMetadata databaseMetadata = DatabaseMetadata.lookUpFrom(tempDataDir);
    assertThat(databaseMetadata).isNotNull();
    assertThat(databaseMetadata.getVersion()).isEqualTo(42);
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
