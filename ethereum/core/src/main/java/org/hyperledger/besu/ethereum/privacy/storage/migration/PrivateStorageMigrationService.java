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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_0_0;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_4_0;

import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateStorageMigrationService {

  private static final Logger LOG = LoggerFactory.getLogger(PrivateStorageMigrationService.class);

  private final PrivateStateStorage privateStateStorage;
  private final boolean migrationFlag;
  private final Supplier<PrivateStorageMigration> migrationBuilder;

  public PrivateStorageMigrationService(
      final PrivateStateStorage privateStateStorage,
      final boolean migrationFlag,
      final Supplier<PrivateStorageMigration> migrationBuilder) {
    this.privateStateStorage = privateStateStorage;
    this.migrationFlag = migrationFlag;
    this.migrationBuilder = migrationBuilder;
  }

  /**
   * Migration only happens if the system detects that the private schema version is lower than
   * version 2 (1.4.x), and the user has set the` privacy-enable-database-migration` option.
   */
  public void runMigrationIfRequired() {
    final int schemaVersion = privateStateStorage.getSchemaVersion();

    if (schemaVersion >= SCHEMA_VERSION_1_4_0) {
      LOG.debug("Private database metadata does not require migration.");
      return;
    }

    /*
     If this is a new database, we need to set the version and no migration is required
    */
    if (privateStateStorage.isEmpty()) {
      privateStateStorage.updater().putDatabaseVersion(SCHEMA_VERSION_1_4_0).commit();
      LOG.debug("Private database metadata does not require migration.");
      return;
    }

    if (schemaVersion == SCHEMA_VERSION_1_0_0 && !migrationFlag) {
      final String message =
          "Private database metadata requires migration. For more information check the 1.4 changelog.";
      LOG.warn(message);
      throw new PrivateStorageMigrationException(message);
    }

    if (schemaVersion == SCHEMA_VERSION_1_0_0 && migrationFlag) {
      LOG.info(
          "Private database metadata requires migration and `privacy-enable-database-migration` was set. Starting migration!");
      migrationBuilder.get().migratePrivateStorage();
    }
  }
}
