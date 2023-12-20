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
package org.hyperledger.besu.ethereum.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(VersionMetadata.class);

  /** Represents an unknown Besu version in the database metadata file */
  public static final String BESU_VERSION_UNKNOWN = "UNKNOWN";

  private static final String METADATA_FILENAME = "VERSION_METADATA.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String besuVersion;

  /**
   * Get the version of Besu that is running.
   *
   * @return the version of Besu
   */
  public static String getRuntimeVersion() {
    return VersionMetadata.class.getPackage().getImplementationVersion();
  }

  @JsonCreator
  public VersionMetadata(@JsonProperty("besuVersion") final String besuVersion) {
    this.besuVersion = besuVersion;
  }

  public String getBesuVersion() {
    return besuVersion;
  }

  public static VersionMetadata lookUpFrom(final Path dataDir) throws IOException {
    LOG.info("Lookup version metadata file in data directory: {}", dataDir.toString());
    return resolveDatabaseMetadata(getDefaultMetadataFile(dataDir));
  }

  public void writeToDirectory(final Path dataDir) throws IOException {
    MAPPER.writeValue(getDefaultMetadataFile(dataDir), this);
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static VersionMetadata resolveDatabaseMetadata(final File metadataFile)
      throws IOException {
    VersionMetadata databaseMetadata;
    try {
      databaseMetadata = MAPPER.readValue(metadataFile, VersionMetadata.class);
      LOG.info("Existing version data detected. Besu version {}", databaseMetadata.besuVersion);
    } catch (FileNotFoundException fnfe) {
      databaseMetadata = new VersionMetadata(BESU_VERSION_UNKNOWN);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          java.lang.String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    return databaseMetadata;
  }
}
