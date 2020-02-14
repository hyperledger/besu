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
package org.hyperledger.besu.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_0_0;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_4_0;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage.Updater;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigration;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigrationException;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigrationService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateStorageMigrationServiceTest {

  @Mock private PrivateStateStorage privateStateStorage;
  @Mock private PrivateStorageMigration migration;

  private PrivateStorageMigrationService migrationService;

  @Test
  public void migrationShouldNotRunIfDatabaseIsFreshAndVersionShouldBeSet() {
    when(privateStateStorage.getSchemaVersion()).thenReturn(SCHEMA_VERSION_1_0_0);
    when(privateStateStorage.isEmpty()).thenReturn(true);
    final Updater privateStateStorageUpdater = mock(Updater.class);
    when(privateStateStorage.updater()).thenReturn(privateStateStorageUpdater);
    when(privateStateStorageUpdater.putDatabaseVersion(anyInt()))
        .thenReturn(privateStateStorageUpdater);

    migrationService =
        new PrivateStorageMigrationService(privateStateStorage, true, () -> migration);

    migrationService.runMigrationIfRequired();

    verify(privateStateStorageUpdater).putDatabaseVersion(eq(SCHEMA_VERSION_1_4_0));
    verifyNoInteractions(migration);
  }

  @Test
  public void migrationShouldNotRunIfSchemaVersionIsGreaterThanOne() {
    when(privateStateStorage.getSchemaVersion()).thenReturn(SCHEMA_VERSION_1_4_0);

    migrationService =
        new PrivateStorageMigrationService(privateStateStorage, true, () -> migration);

    migrationService.runMigrationIfRequired();

    verifyNoInteractions(migration);
  }

  @Test
  public void migrationShouldNotRunIfFlagIsNotSetEvenIfVersionRequiresMigration() {
    when(privateStateStorage.getSchemaVersion()).thenReturn(SCHEMA_VERSION_1_0_0);
    when(privateStateStorage.isEmpty()).thenReturn(false);

    migrationService =
        new PrivateStorageMigrationService(privateStateStorage, false, () -> migration);

    final Throwable thrown = catchThrowable(() -> migrationService.runMigrationIfRequired());
    assertThat(thrown)
        .isInstanceOf(PrivateStorageMigrationException.class)
        .hasMessageContaining("Private database metadata requires migration");

    verifyNoInteractions(migration);
  }

  @Test
  public void migrationShouldRunIfVersionIsOneAndFlagIsSet() {
    when(privateStateStorage.getSchemaVersion()).thenReturn(SCHEMA_VERSION_1_0_0);
    when(privateStateStorage.isEmpty()).thenReturn(false);

    migrationService =
        new PrivateStorageMigrationService(privateStateStorage, true, () -> migration);

    migrationService.runMigrationIfRequired();

    verify(migration).migratePrivateStorage();
  }
}
