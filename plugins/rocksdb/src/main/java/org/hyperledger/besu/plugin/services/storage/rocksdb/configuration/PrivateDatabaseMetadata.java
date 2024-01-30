/*
 * Copyright Hyperledger Besu Contributors.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Database metadata. */
public class PrivateDatabaseMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(PrivateDatabaseMetadata.class);

  private static final String METADATA_FILENAME = "DATABASE_METADATA.json";
  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());
  private final PrivateVersionedStorageFormat versionedStorageFormat;

  private PrivateDatabaseMetadata(final PrivateVersionedStorageFormat versionedStorageFormat) {
    this.versionedStorageFormat = versionedStorageFormat;
  }

  public static PrivateDatabaseMetadata defaultForNewDb() {
    return new PrivateDatabaseMetadata(PrivateVersionedStorageFormat.defaultForNewDB());
  }

  public PrivateVersionedStorageFormat getPrivateVersionedStorageFormat() {
    return versionedStorageFormat;
  }

  /**
   * Look up database metadata.
   *
   * @param dataDir the data dir
   * @return the database metadata
   * @throws IOException the io exception
   */
  public static PrivateDatabaseMetadata lookUpFrom(final Path dataDir) throws IOException {
    LOG.info("Lookup private database metadata file in data directory: {}", dataDir.toString());
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
    MAPPER.writeValue(file, new V1(versionedStorageFormat.getPrivacyVersion()));
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static PrivateDatabaseMetadata resolveDatabaseMetadata(final File metadataFile)
      throws IOException {
    try {
      return tryReadV1(metadataFile);
    } catch (FileNotFoundException fnfe) {
      throw new IllegalStateException(
          "Private database exists but metadata file "
              + metadataFile.toString()
              + " not found, without it there is no safe way to open the private database");
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          String.format(
              "Invalid private database metadata file %s", metadataFile.getAbsolutePath()),
          jpe);
    }
  }

  private static PrivateDatabaseMetadata tryReadV1(final File metadataFile) throws IOException {
    final V1 v1 = MAPPER.readValue(metadataFile, V1.class);
    final var versionedStorageFormat =
        switch (v1.privacyVersion) {
          case 1 -> PrivateVersionedStorageFormat.ORIGINAL;
          default -> throw new IllegalStateException(
              "Unsupported private database version: " + v1.privacyVersion);
        };

    return new PrivateDatabaseMetadata(versionedStorageFormat);
  }

  @Override
  public String toString() {
    return "privateVersionedStorageFormat=" + versionedStorageFormat;
  }

  @JsonSerialize
  @SuppressWarnings("unused")
  private record V1(int privacyVersion) {}
  ;
}
