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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseMetadata {
  private static final Logger LOG = LogManager.getLogger();

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static ObjectMapper MAPPER = new ObjectMapper();
  private final int version;

  @JsonCreator
  public DatabaseMetadata(@JsonProperty("version") final int version) {
    this.version = version;
  }

  public int getVersion() {
    return version;
  }

  public static DatabaseMetadata lookUpFrom(final Path databaseDir, final Path dataDir)
      throws IOException {
    LOG.info("Lookup database metadata file in data directory: {}", dataDir.toString());
    File metadataFile = getDefaultMetadataFile(dataDir);
    final boolean shouldLookupInDatabaseDir = !metadataFile.exists();
    if (shouldLookupInDatabaseDir) {
      LOG.info(
          "Database metadata file not found in data directory. Lookup in database directory: {}",
          databaseDir.toString());
      metadataFile = getDefaultMetadataFile(databaseDir);
    }
    DatabaseMetadata databaseMetadata;
    try {
      databaseMetadata = MAPPER.readValue(metadataFile, DatabaseMetadata.class);
    } catch (FileNotFoundException fnfe) {
      databaseMetadata = new DatabaseMetadata(0);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    if (shouldLookupInDatabaseDir) {
      LOG.warn(
          "Database metadata file has been copied from old location (database directory). Be aware that the old file might be removed in future release.");
      writeToDirectory(databaseMetadata, dataDir);
    }
    return databaseMetadata;
  }

  public void writeToDirectory(final Path databaseDir) throws IOException {
    writeToDirectory(this, databaseDir);
  }

  private static void writeToDirectory(
      final DatabaseMetadata databaseMetadata, final Path databaseDir) throws IOException {
    MAPPER.writeValue(getDefaultMetadataFile(databaseDir), databaseMetadata);
  }

  private static File getDefaultMetadataFile(final Path databaseDir) {
    return databaseDir.resolve(METADATA_FILENAME).toFile();
  }
}
