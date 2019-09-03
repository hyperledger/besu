/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RocksDbStorageProviderTest {

  @Mock private RocksDbConfiguration rocksDbConfiguration;
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void shouldCreateCorrectMetadataFileForLatestVersion() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    when(rocksDbConfiguration.getDatabaseDir()).thenReturn(tempDatabaseDir);
    RocksDbStorageProvider.create(rocksDbConfiguration, metricsSystem);
    assertEquals(
        RocksDbStorageProvider.DEFAULT_VERSION,
        DatabaseMetadata.fromDirectory(rocksDbConfiguration.getDatabaseDir()).getVersion());
  }

  @Test
  public void shouldDetectVersion0DatabaseIfNoMetadataFileFound() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    when(rocksDbConfiguration.getDatabaseDir()).thenReturn(tempDatabaseDir);
    RocksDbStorageProvider.create(rocksDbConfiguration, metricsSystem);
    assertEquals(0, DatabaseMetadata.fromDirectory(tempDatabaseDir).getVersion());
  }

  @Test
  public void shouldDetectCorrectVersionIfMetadataFileExists() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    new DatabaseMetadata(1).writeToDirectory(tempDatabaseDir);
    when(rocksDbConfiguration.getDatabaseDir()).thenReturn(tempDatabaseDir);
    RocksDbStorageProvider.create(rocksDbConfiguration, metricsSystem);
    assertEquals(1, DatabaseMetadata.fromDirectory(tempDatabaseDir).getVersion());
  }

  @Test
  public void shouldThrowExceptionWhenVersionNumberIsInvalid() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();
    new DatabaseMetadata(-1).writeToDirectory(tempDatabaseDir);
    when(rocksDbConfiguration.getDatabaseDir()).thenReturn(tempDatabaseDir);
    assertThatThrownBy(() -> RocksDbStorageProvider.create(rocksDbConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowExceptionWhenMetaDataFileIsCorrupted() throws Exception {
    final Path tempDatabaseDir = temporaryFolder.newFolder().toPath().resolve("db");
    Files.createDirectories(tempDatabaseDir);
    when(rocksDbConfiguration.getDatabaseDir()).thenReturn(tempDatabaseDir);
    tempDatabaseDir.resolve("IDENTITY").toFile().createNewFile();

    final String badVersion = "{\"🦄\":1}";
    Files.write(
        tempDatabaseDir.resolve(DatabaseMetadata.METADATA_FILENAME),
        badVersion.getBytes(Charset.defaultCharset()));
    assertThatThrownBy(() -> RocksDbStorageProvider.create(rocksDbConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);

    final String badValue = "{\"version\":\"iomedae\"}";
    Files.write(
        tempDatabaseDir.resolve(DatabaseMetadata.METADATA_FILENAME),
        badValue.getBytes(Charset.defaultCharset()));
    assertThatThrownBy(() -> RocksDbStorageProvider.create(rocksDbConfiguration, metricsSystem))
        .isInstanceOf(IllegalStateException.class);
  }
}
