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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseMetadata.class);

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final int version;

  private Optional<Integer> privacyVersion;

  @JsonCreator
  public DatabaseMetadata(@JsonProperty("version") final int version) {
    this(version, Optional.empty());
  }

  public DatabaseMetadata(final int version, final Optional<Integer> privacyVersion) {
    this.version = version;
    this.privacyVersion = privacyVersion;
  }

  public DatabaseMetadata(final int version, final int privacyVersion) {
    this(version, Optional.of(privacyVersion));
  }

  public int getVersion() {
    return version;
  }

  @JsonSetter("privacyVersion")
  public void setPrivacyVersion(final int privacyVersion) {
    this.privacyVersion = Optional.of(privacyVersion);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonGetter("privacyVersion")
  public Integer getPrivacyVersion() {
    return privacyVersion.orElse(null);
  }

  public Optional<Integer> maybePrivacyVersion() {
    return privacyVersion;
  }

  public static DatabaseMetadata lookUpFrom(final Path dataDir) throws IOException {
    LOG.info("Lookup database metadata file in data directory: {}", dataDir.toString());
    return resolveDatabaseMetadata(getDefaultMetadataFile(dataDir));
  }

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
      databaseMetadata = new DatabaseMetadata(0, 0);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    return databaseMetadata;
  }
}
