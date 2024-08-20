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
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.DatabaseMetadata;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.PrivacyVersionedStorageFormat;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.VersionedStorageFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes a public storage factory and enables creating independently versioned privacy storage
 * objects which have the same features as the supported public storage factory
 */
public class RocksDBKeyValuePrivacyStorageFactory implements PrivacyKeyValueStorageFactory {
  private static final Logger LOG =
      LoggerFactory.getLogger(RocksDBKeyValuePrivacyStorageFactory.class);
  private static final Set<PrivacyVersionedStorageFormat> SUPPORTED_VERSIONS =
      EnumSet.of(
          PrivacyVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION,
          PrivacyVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION);
  private static final String PRIVATE_DATABASE_PATH = "private";
  private final RocksDBKeyValueStorageFactory publicFactory;
  private DatabaseMetadata databaseMetadata;

  /**
   * Instantiates a new RocksDb key value privacy storage factory.
   *
   * @param publicFactory the public factory
   */
  public RocksDBKeyValuePrivacyStorageFactory(final RocksDBKeyValueStorageFactory publicFactory) {
    this.publicFactory = publicFactory;
  }

  @Override
  public String getName() {
    return "rocksdb-privacy";
  }

  @Override
  public KeyValueStorage create(
      final SegmentIdentifier segment,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem)
      throws StorageException {
    if (databaseMetadata == null) {
      try {
        databaseMetadata = readDatabaseMetadata(commonConfiguration);
      } catch (final IOException e) {
        throw new StorageException("Failed to retrieve the RocksDB database meta version", e);
      }
    }

    return publicFactory.create(segment, commonConfiguration, metricsSystem);
  }

  @Override
  public SegmentedKeyValueStorage create(
      final List<SegmentIdentifier> segments,
      final BesuConfiguration commonConfiguration,
      final MetricsSystem metricsSystem)
      throws StorageException {
    if (databaseMetadata == null) {
      try {
        databaseMetadata = readDatabaseMetadata(commonConfiguration);
      } catch (final IOException e) {
        throw new StorageException("Failed to retrieve the RocksDB database meta version", e);
      }
    }

    return publicFactory.create(segments, commonConfiguration, metricsSystem);
  }

  @Override
  public boolean isSegmentIsolationSupported() {
    return publicFactory.isSegmentIsolationSupported();
  }

  @Override
  public boolean isSnapshotIsolationSupported() {
    return publicFactory.isSnapshotIsolationSupported();
  }

  @Override
  public void close() throws IOException {
    publicFactory.close();
  }

  /**
   * The METADATA.json is located in the dataDir. Pre-1.3 it is located in the databaseDir. If the
   * private database exists there may be a "privacyVersion" field in the metadata file otherwise
   * use the default version
   */
  private DatabaseMetadata readDatabaseMetadata(final BesuConfiguration commonConfiguration)
      throws IOException {
    final Path dataDir = commonConfiguration.getDataPath();
    final boolean privacyDatabaseExists =
        commonConfiguration.getStoragePath().resolve(PRIVATE_DATABASE_PATH).toFile().exists();
    final boolean privacyMetadataExists = DatabaseMetadata.isPresent(dataDir);
    DatabaseMetadata privacyMetadata;
    if (privacyDatabaseExists && !privacyMetadataExists) {
      throw new StorageException(
          "Privacy database exists but metadata file not found, without it there is no safe way to open the database");
    }
    if (privacyMetadataExists) {
      final var existingPrivacyMetadata = DatabaseMetadata.lookUpFrom(dataDir);
      final var maybeExistingPrivacyVersion =
          existingPrivacyMetadata.getVersionedStorageFormat().getPrivacyVersion();
      if (maybeExistingPrivacyVersion.isEmpty()) {
        privacyMetadata = existingPrivacyMetadata.upgradeToPrivacy();
        privacyMetadata.writeToDirectory(dataDir);
        LOG.info(
            "Upgraded existing database at {} to privacy database. Metadata {}",
            dataDir,
            existingPrivacyMetadata);
      } else {
        privacyMetadata = existingPrivacyMetadata;
        final int existingPrivacyVersion = maybeExistingPrivacyVersion.getAsInt();
        final var runtimeVersion =
            PrivacyVersionedStorageFormat.defaultForNewDB(
                commonConfiguration.getDataStorageConfiguration());

        if (existingPrivacyVersion > runtimeVersion.getPrivacyVersion().getAsInt()) {
          final var maybeDowngradedMetadata =
              handleVersionDowngrade(dataDir, privacyMetadata, runtimeVersion);
          if (maybeDowngradedMetadata.isPresent()) {
            privacyMetadata = maybeDowngradedMetadata.get();
            privacyMetadata.writeToDirectory(dataDir);
          }
        } else if (existingPrivacyVersion < runtimeVersion.getPrivacyVersion().getAsInt()) {
          final var maybeUpgradedMetadata =
              handleVersionUpgrade(dataDir, privacyMetadata, runtimeVersion);
          if (maybeUpgradedMetadata.isPresent()) {
            privacyMetadata = maybeUpgradedMetadata.get();
            privacyMetadata.writeToDirectory(dataDir);
          }
        } else {
          LOG.info("Existing privacy database at {}. Metadata {}", dataDir, privacyMetadata);
        }
      }
    } else {
      privacyMetadata = DatabaseMetadata.defaultForNewPrivateDb();
      LOG.info(
          "No existing private database at {}. Using default metadata for new db {}",
          dataDir,
          privacyMetadata);
      Files.createDirectories(dataDir);
      privacyMetadata.writeToDirectory(dataDir);
    }

    if (!SUPPORTED_VERSIONS.contains(privacyMetadata.getVersionedStorageFormat())) {
      final String message = "Unsupported RocksDB Metadata version of: " + privacyMetadata;
      LOG.error(message);
      throw new StorageException(message);
    }

    return privacyMetadata;
  }

  private Optional<DatabaseMetadata> handleVersionDowngrade(
      final Path dataDir,
      final DatabaseMetadata existingPrivacyMetadata,
      final VersionedStorageFormat runtimeVersion) {
    // here we put the code, or the messages, to perform an automated, or manual, downgrade of the
    // database, if supported, otherwise we just prevent Besu from starting since it will not
    // recognize the newer version.
    // In case we do an automated downgrade, then we also need to update the metadata on disk to
    // reflect the change to the runtime version, and return it.

    // Besu supports both formats of receipts so no downgrade is needed
    if (runtimeVersion == PrivacyVersionedStorageFormat.BONSAI_WITH_VARIABLES
        || runtimeVersion == PrivacyVersionedStorageFormat.FOREST_WITH_VARIABLES) {
      LOG.warn(
          "Database contains compacted receipts but receipt compaction is not enabled, new receipts  will "
              + "be not stored in the compacted format. If you want to remove compacted receipts from the "
              + "database it is necessary to resync Besu. Besu can support both compacted and non-compacted receipts.");
      return Optional.empty();
    }

    // for the moment there are supported automated downgrades, so we just fail.
    String error =
        String.format(
            "Database unsafe downgrade detect: DB at %s is %s with version %s but version %s is expected. "
                + "Please check your config and review release notes for supported downgrade procedures.",
            dataDir,
            existingPrivacyMetadata.getVersionedStorageFormat().getFormat().name(),
            existingPrivacyMetadata.getVersionedStorageFormat().getVersion(),
            runtimeVersion.getVersion());

    throw new StorageException(error);
  }

  private Optional<DatabaseMetadata> handleVersionUpgrade(
      final Path dataDir,
      final DatabaseMetadata existingPrivacyMetadata,
      final VersionedStorageFormat runtimeVersion) {
    // here we put the code, or the messages, to perform an automated, or manual, upgrade of the
    // database.
    // In case we do an automated upgrade, then we also need to update the metadata on disk to
    // reflect the change to the runtime version, and return it.

    // Besu supports both formats of receipts so no upgrade is needed other than updating metadata
    final VersionedStorageFormat existingVersionedStorageFormat =
        existingPrivacyMetadata.getVersionedStorageFormat();
    if ((existingVersionedStorageFormat == PrivacyVersionedStorageFormat.BONSAI_WITH_VARIABLES
            && runtimeVersion == PrivacyVersionedStorageFormat.BONSAI_WITH_RECEIPT_COMPACTION)
        || (existingVersionedStorageFormat == PrivacyVersionedStorageFormat.FOREST_WITH_VARIABLES
            && runtimeVersion == PrivacyVersionedStorageFormat.FOREST_WITH_RECEIPT_COMPACTION)) {
      final DatabaseMetadata metadata = new DatabaseMetadata(runtimeVersion);
      try {
        metadata.writeToDirectory(dataDir);
        return Optional.of(metadata);
      } catch (IOException e) {
        throw new StorageException("Database upgrade to use receipt compaction failed", e);
      }
    }

    // for the moment there are no planned automated upgrades, so we just fail.
    String error =
        String.format(
            "Database unsafe upgrade detect: DB at %s is %s with version %s but version %s is expected. "
                + "Please check your config and review release notes for supported upgrade procedures.",
            dataDir,
            existingVersionedStorageFormat.getFormat().name(),
            existingVersionedStorageFormat.getVersion(),
            runtimeVersion.getVersion());

    throw new StorageException(error);
  }

  @Override
  public int getVersion() {
    return databaseMetadata.getVersionedStorageFormat().getPrivacyVersion().getAsInt();
  }
}
