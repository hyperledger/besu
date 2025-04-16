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

import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Database metadata. */
public class DatabaseMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseMetadata.class);

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
          .enable(SerializationFeature.INDENT_OUTPUT);
  private final VersionedStorageFormat versionedStorageFormat;

  /**
   * Instantiates a new Database metadata.
   *
   * @param versionedStorageFormat the version storage format
   */
  public DatabaseMetadata(final VersionedStorageFormat versionedStorageFormat) {
    this.versionedStorageFormat = versionedStorageFormat;
  }

  /**
   * Return the default metadata for new db for a specific format
   *
   * @param besuConfiguration besu configuration
   * @return the metadata to use for new db
   */
  public static DatabaseMetadata defaultForNewDb(final BesuConfiguration besuConfiguration) {
    return new DatabaseMetadata(
        BaseVersionedStorageFormat.defaultForNewDB(
            besuConfiguration.getDataStorageConfiguration()));
  }

  /**
   * Return the version storage format contained in this metadata
   *
   * @return version storage format
   */
  public VersionedStorageFormat getVersionedStorageFormat() {
    return versionedStorageFormat;
  }

  /**
   * Look up database metadata.
   *
   * @param dataDir the data dir
   * @return the database metadata
   * @throws IOException the io exception
   */
  public static DatabaseMetadata lookUpFrom(final Path dataDir) throws IOException {
    LOG.info("Lookup database metadata file in data directory: {}", dataDir.toString());
    return resolveDatabaseMetadata(getDefaultMetadataFile(dataDir));
  }

  /**
   * Is the metadata file present in the specified data dir?
   *
   * @param dataDir the dir to search for the metadata file
   * @return true is the metadata file exists, false otherwise
   * @throws IOException if there is an error trying to access the metadata file
   */
  public static boolean isPresent(final Path dataDir) throws IOException {
    return getDefaultMetadataFile(dataDir).exists();
  }

  /**
   * Write to directory.
   *
   * @param dataDir the data dir
   * @throws IOException the io exception
   */
  public void writeToDirectory(final Path dataDir) throws IOException {
    writeToFile(getDefaultMetadataFile(dataDir));
  }

  private void writeToFile(final File file) throws IOException {
    MAPPER.writeValue(
        file,
        new V2(
            new MetadataV2(
                versionedStorageFormat.getFormat(), versionedStorageFormat.getVersion())));
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static DatabaseMetadata resolveDatabaseMetadata(final File metadataFile)
      throws IOException {
    try {
      try {
        return tryReadAndMigrateV1(metadataFile);
      } catch (DatabindException dbe) {
        return tryReadV2(metadataFile);
      }
    } catch (FileNotFoundException fnfe) {
      throw new StorageException(
          "Database exists but metadata file "
              + metadataFile.toString()
              + " not found, without it there is no safe way to open the database",
          fnfe);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
  }

  private static DatabaseMetadata tryReadAndMigrateV1(final File metadataFile) throws IOException {
    final V1 v1 = MAPPER.readValue(metadataFile, V1.class);
    // when migrating from v1, this version will automatically migrate the db to the variables
    // storage, so we use the `_WITH_VARIABLES` variants
    final VersionedStorageFormat versionedStorageFormat;
    versionedStorageFormat =
        switch (v1.version()) {
          case 1 -> BaseVersionedStorageFormat.FOREST_WITH_VARIABLES;
          case 2 -> BaseVersionedStorageFormat.BONSAI_WITH_VARIABLES;
          default -> throw new StorageException("Unsupported db version: " + v1.version());
        };

    final DatabaseMetadata metadataV2 = new DatabaseMetadata(versionedStorageFormat);
    // writing the metadata will migrate to v2
    metadataV2.writeToFile(metadataFile);
    return metadataV2;
  }

  private static DatabaseMetadata tryReadV2(final File metadataFile) throws IOException {
    final V2 v2 = MAPPER.readValue(metadataFile, V2.class);
    return new DatabaseMetadata(fromV2(v2.v2));
  }

  private static VersionedStorageFormat fromV2(final MetadataV2 metadataV2) {
    return Arrays.stream(BaseVersionedStorageFormat.values())
        .filter(
            vsf ->
                vsf.getFormat().equals(metadataV2.format())
                    && vsf.getVersion() == metadataV2.version())
        .findFirst()
        .orElseThrow(
            () -> {
              final String message = "Unsupported RocksDB metadata: " + metadataV2;
              LOG.error(message);
              throw new StorageException(message);
            });
  }

  @Override
  public String toString() {
    return "versionedStorageFormat=" + versionedStorageFormat;
  }

  @JsonSerialize
  @SuppressWarnings("unused")
  private record V1(int version) {}

  @JsonSerialize
  @SuppressWarnings("unused")
  private record V2(MetadataV2 v2) {}

  @JsonSerialize
  @SuppressWarnings("unused")
  @JsonIgnoreProperties({"privacyVersion"})
  private record MetadataV2(DataStorageFormat format, int version) {}
}
