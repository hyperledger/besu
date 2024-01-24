/*
 * Copyright Hyperledger Besu contributors.
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
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionMetadata {
  private static final Logger LOG = LoggerFactory.getLogger(VersionMetadata.class);

  /** Represents an unknown Besu version in the version metadata file */
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
    return VersionMetadata.class.getPackage().getImplementationVersion() == null
        ? BESU_VERSION_UNKNOWN
        : VersionMetadata.class.getPackage().getImplementationVersion();
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
    return resolveVersionMetadata(getDefaultMetadataFile(dataDir));
  }

  public void writeToDirectory(final Path dataDir) throws IOException {
    MAPPER.writeValue(getDefaultMetadataFile(dataDir), this);
  }

  private static File getDefaultMetadataFile(final Path dataDir) {
    return dataDir.resolve(METADATA_FILENAME).toFile();
  }

  private static VersionMetadata resolveVersionMetadata(final File metadataFile)
      throws IOException {
    VersionMetadata versionMetadata;
    try {
      versionMetadata = MAPPER.readValue(metadataFile, VersionMetadata.class);
      LOG.info("Existing version data detected. Besu version {}", versionMetadata.besuVersion);
    } catch (FileNotFoundException fnfe) {
      versionMetadata = new VersionMetadata(BESU_VERSION_UNKNOWN);
    } catch (JsonProcessingException jpe) {
      throw new IllegalStateException(
          java.lang.String.format("Invalid metadata file %s", metadataFile.getAbsolutePath()), jpe);
    }
    return versionMetadata;
  }

  /**
   * This function is designed to protect a Besu instance from being unintentionally started at a
   * version of Besu that might be incompatible with the version that last modified the specified
   * data directory. Currently this check is limited to checking that the version is >= the previous
   * version, to avoid accidentally running a lower version of Besu and potentially corrupting data.
   * If the --version-compatibility-protection flag is set to true and the compatibilty checks pass,
   * the version metadata is updated to the current version of Besu.
   */
  public static void performVersionCompatibilityChecks(
      final boolean versionCompatibilityProtection, final Path dataDir) throws IOException {
    final VersionMetadata versionMetaData = VersionMetadata.lookUpFrom(dataDir);
    if (versionMetaData.getBesuVersion().equals(VersionMetadata.BESU_VERSION_UNKNOWN)) {
      // The version isn't known, potentially because the file doesn't exist. Write the latest
      // version to the metadata file.
      LOG.info(
          "No version data detected. Writing Besu version {} to metadata file",
          VersionMetadata.getRuntimeVersion());
      new VersionMetadata(VersionMetadata.getRuntimeVersion()).writeToDirectory(dataDir);
    } else {
      // Check the runtime version against the most recent version as recorded in the version
      // metadata file
      final String installedVersion = VersionMetadata.getRuntimeVersion().split("-", 2)[0];
      final String metadataVersion = versionMetaData.getBesuVersion().split("-", 2)[0];
      final int versionComparison =
          new ComparableVersion(installedVersion).compareTo(new ComparableVersion(metadataVersion));
      if (versionComparison == 0) {
        // Versions match - no-op
      } else if (versionComparison < 0) {
        if (!versionCompatibilityProtection) {
          LOG.warn(
              "Besu version {} is lower than version {} that last started. Allowing startup because --version-compatibility-protection has been disabled.",
              installedVersion,
              metadataVersion);
          // We've allowed startup at an older version of Besu. Since the version in the metadata
          // file records the latest version of
          // Besu to write to the database we'll update the metadata version to this
          // downgraded-version. This avoids the need after a successful
          // downgrade to keep specifying --allow-downgrade on every startup.
          new VersionMetadata(VersionMetadata.getRuntimeVersion()).writeToDirectory(dataDir);
        } else {
          final String message =
              "Besu version "
                  + installedVersion
                  + " is lower than version "
                  + metadataVersion
                  + " that last started. Specify --allow-downgrade to allow Besu to start at "
                  + " the lower version (warning - this may have unrecoverable effects on the database).";
          LOG.error(message, installedVersion, metadataVersion);
          throw new IllegalStateException(message);
        }
      } else {
        LOG.info(
            "Besu version {} is higher than version {} that last started. Updating version metadata.",
            installedVersion,
            metadataVersion);
        new VersionMetadata(VersionMetadata.getRuntimeVersion()).writeToDirectory(dataDir);
      }
    }
  }
}
