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

import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalInt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Database metadata. */
public class DatabaseMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseMetadata.class);

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());
  private final DataStorageFormat format;
  private final int version;

  private final OptionalInt maybePrivacyVersion;
  //
  //  /**
  //   * Instantiates a new Database metadata.
  //   *
  //   * @param version the version
  //   */
  //  @JsonCreator
  //  public DatabaseMetadata(final DataStorageFormat format, @JsonProperty("version") final int
  // version) {
  //    this(format, version, Optional.empty());
  //  }

  /**
   * Instantiates a new Database metadata.
   *
   * @param format the format
   * @param version the version
   */
  public DatabaseMetadata(final DataStorageFormat format, final int version) {
    this(format, version, OptionalInt.empty());
  }

  /**
   * Instantiates a new Database metadata.
   *
   * @param format the format
   * @param version the version
   * @param privacyVersion the privacy version
   */
  public DatabaseMetadata(
      final DataStorageFormat format, final int version, final int privacyVersion) {
    this(format, version, OptionalInt.of(privacyVersion));
  }

  /**
   * Instantiates a new Database metadata.
   *
   * @param format the format
   * @param version the version
   * @param maybePrivacyVersion the optional privacy version
   */
  private DatabaseMetadata(
      final DataStorageFormat format, final int version, final OptionalInt maybePrivacyVersion) {
    this.format = format;
    this.version = version;
    this.maybePrivacyVersion = maybePrivacyVersion;
  }
  //
  //  /**
  //   * Instantiates a new Database metadata.
  //   *
  //   * @param version the version
  //   * @param privacyVersion the privacy version
  //   */
  //  public DatabaseMetadata(final DataStorageFormat format, final int version, final int
  // privacyVersion) {
  //    this(format, version, Optional.of(privacyVersion));
  //  }

  public DataStorageFormat getFormat() {
    return format;
  }

  /**
   * Gets version.
   *
   * @return the version
   */
  public int getVersion() {
    return version;
  }

  /**
   * Maybe privacy version.
   *
   * @return the optional
   */
  public OptionalInt maybePrivacyVersion() {
    return maybePrivacyVersion;
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
   * Write to directory.
   *
   * @param dataDir the data dir
   * @throws IOException the io exception
   */
  public void writeToDirectory(final Path dataDir) throws IOException {
    writeToFile(getDefaultMetadataFile(dataDir));
  }

  private void writeToFile(final File file) throws IOException {
    MAPPER.writeValue(file, new V2(new MetadataV2(format, version, maybePrivacyVersion)));
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static DatabaseMetadata resolveDatabaseMetadata(final File metadataFile)
      throws IOException {
    DatabaseMetadata databaseMetadata;
    try {
      try {
        return tryReadAndMigrateV1(metadataFile);
      } catch (DatabindException dbe) {
        return tryReadV2(metadataFile);
      }
    } catch (FileNotFoundException fnfe) {
      databaseMetadata = new DatabaseMetadata(DataStorageFormat.FOREST, 2);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    return databaseMetadata;
  }

  private static DatabaseMetadata tryReadAndMigrateV1(final File metadataFile) throws IOException {
    final V1 v1 = MAPPER.readValue(metadataFile, V1.class);
    final DatabaseMetadata metadataV1 =
        new DatabaseMetadata(DataStorageFormat.fromLegacyVersion(v1.version), 2, v1.privacyVersion);
    // writing the metadata will migrate to v2
    metadataV1.writeToFile(metadataFile);
    return metadataV1;
  }

  private static DatabaseMetadata tryReadV2(final File metadataFile) throws IOException {
    final V2 v2 = MAPPER.readValue(metadataFile, V2.class);
    return new DatabaseMetadata(v2.v2.format, v2.v2.version, v2.v2.privacyVersion);
  }

  @Override
  public String toString() {
    return "format="
        + format
        + ", version="
        + version
        + ((maybePrivacyVersion.isPresent()) ? ", privacyVersion=" + maybePrivacyVersion : "");
  }

  private static class V1 {
    @JsonProperty int version;
    @JsonProperty OptionalInt privacyVersion;
  }

  private static class V2 {
    private final MetadataV2 v2;

    public V2(final MetadataV2 v2) {
      this.v2 = v2;
    }
  }

  private static class MetadataV2 {
    private final DataStorageFormat format;
    private final int version;
    private final OptionalInt privacyVersion;

    public MetadataV2(
        final DataStorageFormat format, final int version, final OptionalInt privacyVersion) {
      this.format = format;
      this.version = version;
      this.privacyVersion = privacyVersion;
    }
  }
}
