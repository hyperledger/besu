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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Database metadata. */
public class DatabaseMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseMetadata.class);

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());
  private final int version;

  private Optional<String> besuVersion;
  private Optional<Integer> privacyVersion;

  /**
   * Instantiates a new Database metadata.
   *
   * @param version the version
   */
  @JsonCreator
  public DatabaseMetadata(
      @JsonProperty("version") final int version,
      @JsonProperty("besuVersion") final Optional<String> besuVersion) {
    this(version, besuVersion, Optional.empty());
  }

  /**
   * Instantiates a new Database metadata.
   *
   * @param version the version
   * @param privacyVersion the privacy version
   */
  public DatabaseMetadata(
      final int version,
      final Optional<String> besuVersion,
      final Optional<Integer> privacyVersion) {
    this.version = version;
    this.privacyVersion = privacyVersion;
    this.besuVersion = besuVersion;
  }

  /**
   * Instantiates a new Database metadata.
   *
   * @param version the version
   * @param privacyVersion the privacy version
   */
  public DatabaseMetadata(final int version, final String besuVersion, final int privacyVersion) {
    this(version, Optional.of(besuVersion), Optional.of(privacyVersion));
  }

  /**
   * Gets version.
   *
   * @return the version
   */
  public int getVersion() {
    return version;
  }

  @JsonSetter("besuVersion")
  public void setBesuVersion(final String besuVersion) {
    this.besuVersion = Optional.of(besuVersion);
  }

  /**
   * Gets version of Besu.
   *
   * @return the version of Besu
   */
  @JsonGetter("besuVersion")
  public String getBesuVersion() {
    if (besuVersion != null) {
      return besuVersion.orElse("UNKNOWN");
    }
    return "UNKNOWN";
  }

  /**
   * Sets privacy version.
   *
   * @param privacyVersion the privacy version
   */
  @JsonSetter("privacyVersion")
  public void setPrivacyVersion(final int privacyVersion) {
    this.privacyVersion = Optional.of(privacyVersion);
  }

  /**
   * Gets privacy version.
   *
   * @return the privacy version
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter("privacyVersion")
  public Integer getPrivacyVersion() {
    return privacyVersion.orElse(null);
  }

  /**
   * Maybe privacy version.
   *
   * @return the optional
   */
  public Optional<Integer> maybePrivacyVersion() {
    return privacyVersion;
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
    try {
      final DatabaseMetadata currentMetadata =
          MAPPER.readValue(getDefaultMetadataFile(dataDir), DatabaseMetadata.class);
      if (currentMetadata.maybePrivacyVersion().isPresent()) {
        setPrivacyVersion(currentMetadata.getPrivacyVersion());
      }
      MAPPER.writeValue(getDefaultMetadataFile(dataDir), this);
    } catch (FileNotFoundException fnfe) {
      MAPPER.writeValue(getDefaultMetadataFile(dataDir), this);
    }
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static DatabaseMetadata resolveDatabaseMetadata(final File metadataFile)
      throws IOException {
    DatabaseMetadata databaseMetadata;
    try {
      databaseMetadata = MAPPER.readValue(metadataFile, DatabaseMetadata.class);
    } catch (FileNotFoundException fnfe) {
      databaseMetadata = new DatabaseMetadata(1, Optional.of("UNKNOWN"));
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    return databaseMetadata;
  }
}
