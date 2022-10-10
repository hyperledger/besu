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
package org.hyperledger.besu.ethereum.api.query.cache;

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

public class LogBloomCacheMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(LogBloomCacheMetadata.class);

  public static final int DEFAULT_VERSION = 3;

  private static final String METADATA_FILENAME = "CACHE_METADATA.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final int version;

  @JsonCreator
  public LogBloomCacheMetadata(@JsonProperty("version") final int version) {
    this.version = version;
  }

  public int getVersion() {
    return version;
  }

  public static LogBloomCacheMetadata lookUpFrom(final Path dataDir) throws IOException {
    LOG.info("Lookup cache metadata file in data directory: {}", dataDir.toString());
    return resolveDatabaseMetadata(getDefaultMetadataFile(dataDir));
  }

  public void writeToDirectory(final Path dataDir) throws IOException {
    MAPPER.writeValue(getDefaultMetadataFile(dataDir), this);
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static LogBloomCacheMetadata resolveDatabaseMetadata(final File metadataFile)
      throws IOException {
    LogBloomCacheMetadata databaseMetadata;
    try {
      databaseMetadata = MAPPER.readValue(metadataFile, LogBloomCacheMetadata.class);
    } catch (FileNotFoundException fnfe) {
      databaseMetadata = new LogBloomCacheMetadata(0);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    return databaseMetadata;
  }
}
