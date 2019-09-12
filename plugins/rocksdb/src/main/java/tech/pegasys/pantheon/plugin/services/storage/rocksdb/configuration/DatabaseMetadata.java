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
package tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DatabaseMetadata {
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

  public static DatabaseMetadata fromDirectory(final Path databaseDir) throws IOException {
    final File metadataFile = getDefaultMetadataFile(databaseDir);
    try {
      return MAPPER.readValue(metadataFile, DatabaseMetadata.class);
    } catch (FileNotFoundException fnfe) {
      return new DatabaseMetadata(0);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
  }

  public void writeToDirectory(final Path databaseDir) throws IOException {
    MAPPER.writeValue(getDefaultMetadataFile(databaseDir), this);
  }

  private static File getDefaultMetadataFile(final Path databaseDir) {
    return databaseDir.resolve(METADATA_FILENAME).toFile();
  }
}
