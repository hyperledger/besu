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
 *
 */
package org.hyperledger.besu.plugin.services.storage.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.plugin.services.storage.GlobalKeyValueStorageTransaction.NON_GLOBAL_TRANSACTION_FIELD;

import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.GlobalKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SnappableKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

public class GlobalKeyValueStorageTransactionTest<S> {

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();

  @Test
  public void commitSegmentsSeparatelyWithoutGlobalTransaction() throws Exception {

    final StorageProvider store = createKeyValueStorageProvider();

    final KeyValueStorage barSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    final KeyValueStorageTransaction txBar = barSegment.startTransaction();

    final KeyValueStorage fooSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    final KeyValueStorageTransaction txFoo = fooSegment.startTransaction();

    txBar.put(
        Bytes.fromHexString("0001").toArrayUnsafe(), Bytes.fromHexString("0FFF").toArrayUnsafe());
    txBar.commit();

    txFoo.put(
        Bytes.fromHexString("0001").toArrayUnsafe(), Bytes.fromHexString("0FFF").toArrayUnsafe());

    assertThat(fooSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isEmpty();
    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isNotEmpty();

    txFoo.commit();

    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isNotEmpty();

    store.close();
  }

  @Test
  public void commitSegmentsWithGlobalTransaction() throws Exception {

    final StorageProvider store = createKeyValueStorageProvider();

    GlobalKeyValueStorageTransaction<?> globalKeyValueStorageTransaction =
        store.createGlobalKeyValueStorageTransaction();

    final KeyValueStorage barSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE, barSegment);

    final KeyValueStorage fooSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE, fooSegment);

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE,
        Bytes.fromHexString("0001").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE,
        Bytes.fromHexString("0002").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isEmpty();
    assertThat(fooSegment.get(Bytes.fromHexString("0002").toArrayUnsafe())).isEmpty();

    globalKeyValueStorageTransaction.commit();

    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isNotEmpty();
    assertThat(fooSegment.get(Bytes.fromHexString("0002").toArrayUnsafe())).isNotEmpty();

    store.close();
  }

  @Test
  public void rollbackSegmentsWithGlobalTransaction() throws Exception {

    final StorageProvider store = createKeyValueStorageProvider();

    GlobalKeyValueStorageTransaction<?> globalKeyValueStorageTransaction =
        store.createGlobalKeyValueStorageTransaction();

    final KeyValueStorage barSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE, barSegment);

    final KeyValueStorage fooSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE, fooSegment);

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE,
        Bytes.fromHexString("0001").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE,
        Bytes.fromHexString("0002").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    globalKeyValueStorageTransaction.rollback();

    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isEmpty();
    assertThat(fooSegment.get(Bytes.fromHexString("0002").toArrayUnsafe())).isEmpty();

    store.close();
  }

  @Test
  public void nonGlobalTransactionForKeyValueStorageShouldWork() throws Exception {

    final StorageProvider store = createKeyValueStorageProvider();

    GlobalKeyValueStorageTransaction<?> globalKeyValueStorageTransaction =
        NON_GLOBAL_TRANSACTION_FIELD;

    final KeyValueStorage barSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE, barSegment);

    final KeyValueStorage fooSegment =
        store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE, fooSegment);

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE,
        Bytes.fromHexString("0001").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE,
        Bytes.fromHexString("0002").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    globalKeyValueStorageTransaction.commit();

    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isNotEmpty();
    assertThat(fooSegment.get(Bytes.fromHexString("0002").toArrayUnsafe())).isNotEmpty();

    store.close();
  }

  @Test
  public void nonGlobalTransactionForSnapshotKeyValueStorageShouldWork() throws Exception {

    final StorageProvider store = createKeyValueStorageProvider();

    GlobalKeyValueStorageTransaction<?> globalKeyValueStorageTransaction =
        NON_GLOBAL_TRANSACTION_FIELD;

    final KeyValueStorage barSegment =
        ((SnappableKeyValueStorage)
                store.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE))
            .takeSnapshot();
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE, barSegment);

    final KeyValueStorage fooSegment =
        ((SnappableKeyValueStorage)
                store.getStorageBySegmentIdentifier(
                    KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE))
            .takeSnapshot();
    globalKeyValueStorageTransaction.includeInGlobalTransactionStorage(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE, fooSegment);

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE,
        Bytes.fromHexString("0001").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    globalKeyValueStorageTransaction.put(
        KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE,
        Bytes.fromHexString("0002").toArrayUnsafe(),
        Bytes.fromHexString("0FFF").toArrayUnsafe());

    // Uses the same transaction object for read and write operations, eliminating the need for a
    // commit for snapshots.
    // globalKeyValueStorageTransaction.commit();

    assertThat(barSegment.get(Bytes.fromHexString("0001").toArrayUnsafe())).isNotEmpty();
    assertThat(fooSegment.get(Bytes.fromHexString("0002").toArrayUnsafe())).isNotEmpty();

    store.close();
  }

  // storage provider which uses a temporary directory based rocksdb
  protected StorageProvider createKeyValueStorageProvider() {
    try {
      tempData.create();
      return new KeyValueStorageProviderBuilder()
          .withStorageFactory(
              new RocksDBKeyValueStorageFactory(
                  () ->
                      new RocksDBFactoryConfiguration(
                          1024 /* MAX_OPEN_FILES*/,
                          4 /*BACKGROUND_THREAD_COUNT*/,
                          8388608 /*CACHE_CAPACITY*/,
                          false),
                  Arrays.asList(KeyValueSegmentIdentifier.values()),
                  2,
                  RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS))
          .withCommonConfiguration(
              new BesuConfiguration() {

                @Override
                public Path getStoragePath() {
                  return new File(tempData.getRoot().toString() + File.pathSeparator + "database")
                      .toPath();
                }

                @Override
                public Path getDataPath() {
                  return tempData.getRoot().toPath();
                }

                @Override
                public int getDatabaseVersion() {
                  return 2;
                }
              })
          .withMetricsSystem(new NoOpMetricsSystem())
          .build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
