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
package org.hyperledger.besu.ethereum.privacy.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_0_0;
import static org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage.SCHEMA_VERSION_1_4_0;

import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import org.junit.Before;
import org.junit.Test;

public class PrivateStateKeyValueStorageTest {

  private PrivateStateKeyValueStorage storage;

  @Before
  public void before() {
    storage = new PrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
  }

  @Test
  public void databaseWithoutVersionShouldReturn1_0_x() {
    assertThat(storage.getSchemaVersion()).isEqualTo(SCHEMA_VERSION_1_0_0);
  }

  @Test
  public void databaseSetVersionShouldSetVersion() {
    storage.updater().putDatabaseVersion(123).commit();
    assertThat(storage.getSchemaVersion()).isEqualTo(123);
  }

  @Test
  public void schemaVersion1_0_xHasCorrectValue() {
    assertThat(SCHEMA_VERSION_1_0_0).isEqualTo(1);
  }

  @Test
  public void schemaVersion1_4_xHasCorrectValue() {
    assertThat(SCHEMA_VERSION_1_4_0).isEqualTo(2);
  }
}
