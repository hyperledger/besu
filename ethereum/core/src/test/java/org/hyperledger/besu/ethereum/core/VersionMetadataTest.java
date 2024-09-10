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
package org.hyperledger.besu.ethereum.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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

  @Test
  void dataDirShouldBeCreatedIfNotPresent() throws Exception {
    Files.deleteIfExists(temporaryFolder);
    assertThat(Files.exists(temporaryFolder)).isFalse();

    final VersionMetadata versionMetadata = VersionMetadata.lookUpFrom(temporaryFolder);
    assertThat(versionMetadata).isNotNull();

    assertThat(Files.exists(temporaryFolder)).isTrue();
  }

  static Stream<Arguments> versionTestProvider() {
    return Stream.of(
        Arguments.of("24.4.0", "24.3.3", 1),
        Arguments.of("24.3.3", "24.3.3", 0),
        Arguments.of("24.2.0", "24.3.3", -1),
        Arguments.of("24.3.3", "24.3-develop-59da092", 1),
        Arguments.of("24.3.0", "24.3-develop-59da092", 0),
        Arguments.of("24.3.3", "24.4-develop-59da092", -1),
        Arguments.of("24.2-develop-59da092", "24.3-develop-9999999", -1),
        Arguments.of("24.3-develop-59da092", "24.3-develop-9999999", 0),
        Arguments.of("24.4-develop-59da092", "24.3-develop-9999999", 1),
        Arguments.of("24.4-develop-59da092", "24.3.0", 1),
        Arguments.of("24.4-develop-59da092", "24.4.0", 0),
        Arguments.of("24.4-develop-59da092", "24.4.1", -1));
  }

  @ParameterizedTest
  @MethodSource("versionTestProvider")
  public void assertComparableChecks(
      final String runtimeVersion,
      final String metadataVersion,
      final int expectedComparisonResult) {
    VersionMetadata runtime = new VersionMetadata(runtimeVersion);
    VersionMetadata develop = new VersionMetadata(metadataVersion);
    assertThat(runtime.compareTo(develop)).isEqualTo(expectedComparisonResult);
  }

  @Test
  void compatibilityCheckShouldThrowExceptionIfEnabled() throws Exception {
    // The version file says the last version to start was 23.10.3
    final Path tempDataDir =
        createAndWrite("data", "VERSION_METADATA.json", "{\"besuVersion\":\"23.10.3\"}");

    // The runtime says the current version is 23.10.2 (i.e. a downgrade)
    try (MockedStatic<VersionMetadata> mocked =
        Mockito.mockStatic(VersionMetadata.class, Mockito.CALLS_REAL_METHODS)) {
      mocked.when(VersionMetadata::getRuntimeVersionString).thenReturn("23.10.2");

      final VersionMetadata versionMetadata = VersionMetadata.lookUpFrom(tempDataDir);
      assertThat(versionMetadata).isNotNull();
      assertThat(versionMetadata.getBesuVersion()).isEqualTo("23.10.3");
      assertThatThrownBy(() -> VersionMetadata.versionCompatibilityChecks(true, tempDataDir))
          .isInstanceOf(IllegalStateException.class);
    }

    // Check that the file hasn't been updated
    final String updatedFileContents =
        Files.readString(tempDataDir.resolve("VERSION_METADATA.json"));
    VersionMetadata newVersionMetadata =
        new ObjectMapper().readValue(updatedFileContents, VersionMetadata.class);
    assertThat(newVersionMetadata.getBesuVersion()).isEqualTo("23.10.3");
  }

  @Test
  void compatibilityCheckShouldNotThrowExceptionIfDisabled() throws Exception {
    // The version file says the last version to start was 23.10.3
    final Path tempDataDir =
        createAndWrite("data", "VERSION_METADATA.json", "{\"besuVersion\":\"23.10.3\"}");

    // The runtime says the current version is 23.10.2 (i.e. a downgrade) but we're setting
    // version-compatibility-protection = false so no exception should be thrown
    try (MockedStatic<VersionMetadata> mocked =
        Mockito.mockStatic(VersionMetadata.class, Mockito.CALLS_REAL_METHODS)) {
      mocked.when(VersionMetadata::getRuntimeVersionString).thenReturn("23.10.2");

      final VersionMetadata versionMetadata = VersionMetadata.lookUpFrom(tempDataDir);
      assertThat(versionMetadata).isNotNull();
      assertThat(versionMetadata.getBesuVersion()).isEqualTo("23.10.3");

      assertThatNoException()
          .isThrownBy(() -> VersionMetadata.versionCompatibilityChecks(false, tempDataDir));
    }

    // Check that the file has been updated
    final String updatedFileContents =
        Files.readString(tempDataDir.resolve("VERSION_METADATA.json"));
    VersionMetadata newVersionMetadata =
        new ObjectMapper().readValue(updatedFileContents, VersionMetadata.class);
    assertThat(newVersionMetadata.getBesuVersion()).isEqualTo("23.10.2");
  }

  @Test
  void compatibilityCheckShouldNotThrowExceptionIfResultIsUpgrade() throws Exception {
    final Path tempDataDir =
        createAndWrite("data", "VERSION_METADATA.json", "{\"besuVersion\":\"23.10.3\"}");

    // The runtime says the current version is 23.10.2 (i.e. a downgrade)
    try (MockedStatic<VersionMetadata> mocked =
        Mockito.mockStatic(VersionMetadata.class, Mockito.CALLS_REAL_METHODS)) {
      mocked.when(VersionMetadata::getRuntimeVersionString).thenReturn("23.10.4");

      final VersionMetadata versionMetadata = VersionMetadata.lookUpFrom(tempDataDir);
      assertThat(versionMetadata).isNotNull();
      assertThat(versionMetadata.getBesuVersion()).isEqualTo("23.10.3");

      assertThatNoException()
          .isThrownBy(() -> VersionMetadata.versionCompatibilityChecks(true, tempDataDir));
    }

    // Check that the file has been updated
    final String updatedFileContents =
        Files.readString(tempDataDir.resolve("VERSION_METADATA.json"));
    VersionMetadata newVersionMetadata =
        new ObjectMapper().readValue(updatedFileContents, VersionMetadata.class);
    assertThat(newVersionMetadata.getBesuVersion()).isEqualTo("23.10.4");
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
