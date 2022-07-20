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
package org.hyperledger.besu.plugin.services.storage.leveldb;

import static org.hyperledger.besu.plugin.services.storage.leveldb.LevelDbPlugin.isLevelDbSupported;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.kvstore.AbstractKeyValueStorageTest;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LevelDbKeyValueStorageTest extends AbstractKeyValueStorageTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static final SegmentIdentifier segment =
      new SegmentIdentifier() {
        @Override
        public String getName() {
          return "test";
        }

        @Override
        public byte[] getId() {
          return new byte[] {1};
        }
      };

  @BeforeClass
  public static void loadLevelDb() {
    assumeTrue(isLevelDbSupported());
  }

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return new LevelDbKeyValueStorageFactory(List.of(segment))
        .create(segment, folder.newFolder().toPath());
  }

  @Test
  public void shouldRunTests() {}
}
