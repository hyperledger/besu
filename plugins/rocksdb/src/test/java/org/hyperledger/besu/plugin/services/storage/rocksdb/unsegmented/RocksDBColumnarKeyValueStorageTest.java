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
package org.hyperledger.besu.plugin.services.storage.rocksdb.unsegmented;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.kvstore.AbstractKeyValueStorageTest;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage.Transaction;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RocksDBColumnarKeyValueStorageTest extends AbstractKeyValueStorageTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void assertClear() throws Exception {
    final byte[] key = bytesFromHexString("0001");
    final byte[] val1 = bytesFromHexString("0FFF");
    final byte[] val2 = bytesFromHexString("1337");
    final SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store = createSegmentedStore();
    RocksDbSegmentIdentifier segment = store.getSegmentIdentifierByName(TestSegment.FOO);
    KeyValueStorage duplicateSegmentRef =
        new SegmentedKeyValueStorageAdapter<>(TestSegment.FOO, store);

    final Consumer<byte[]> insert =
        value -> {
          final Transaction<RocksDbSegmentIdentifier> tx = store.startTransaction();
          tx.put(segment, key, value);
          tx.commit();
        };

    // insert val:
    insert.accept(val1);
    assertThat(store.get(segment, key).orElse(null)).isEqualTo(val1);
    assertThat(duplicateSegmentRef.get(key).orElse(null)).isEqualTo(val1);

    // clear and assert empty:
    store.clear(segment);
    assertThat(store.get(segment, key)).isEmpty();
    assertThat(duplicateSegmentRef.get(key)).isEmpty();

    // insert into empty:
    insert.accept(val2);
    assertThat(store.get(segment, key).orElse(null)).isEqualTo(val2);
    assertThat(duplicateSegmentRef.get(key).orElse(null)).isEqualTo(val2);

    store.close();
  }

  @Test
  public void twoSegmentsAreIndependent() throws Exception {
    final SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store = createSegmentedStore();

    final Transaction<RocksDbSegmentIdentifier> tx = store.startTransaction();
    tx.put(
        store.getSegmentIdentifierByName(TestSegment.BAR),
        bytesFromHexString("0001"),
        bytesFromHexString("0FFF"));
    tx.commit();

    final Optional<byte[]> result =
        store.get(store.getSegmentIdentifierByName(TestSegment.FOO), bytesFromHexString("0001"));

    assertThat(result).isEmpty();

    store.close();
  }

  @Test
  public void canRemoveThroughSegmentIteration() throws Exception {
    // we're looping this in order to catch intermittent failures when rocksdb objects are not close
    // properly
    for (int i = 0; i < 50; i++) {
      final SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store = createSegmentedStore();
      final RocksDbSegmentIdentifier fooSegment = store.getSegmentIdentifierByName(TestSegment.FOO);
      final RocksDbSegmentIdentifier barSegment = store.getSegmentIdentifierByName(TestSegment.BAR);

      final Transaction<RocksDbSegmentIdentifier> tx = store.startTransaction();
      tx.put(fooSegment, bytesOf(1), bytesOf(1));
      tx.put(fooSegment, bytesOf(2), bytesOf(2));
      tx.put(fooSegment, bytesOf(3), bytesOf(3));
      tx.put(barSegment, bytesOf(4), bytesOf(4));
      tx.put(barSegment, bytesOf(5), bytesOf(5));
      tx.put(barSegment, bytesOf(6), bytesOf(6));
      tx.commit();

      store
          .streamKeys(fooSegment)
          .forEach(
              key -> {
                if (!Arrays.equals(key, bytesOf(3))) store.tryDelete(fooSegment, key);
              });
      store
          .streamKeys(barSegment)
          .forEach(
              key -> {
                if (!Arrays.equals(key, bytesOf(4))) store.tryDelete(barSegment, key);
              });

      for (final RocksDbSegmentIdentifier segment : Set.of(fooSegment, barSegment)) {
        assertThat(store.streamKeys(segment).count()).isEqualTo(1);
      }

      assertThat(store.get(fooSegment, bytesOf(1))).isEmpty();
      assertThat(store.get(fooSegment, bytesOf(2))).isEmpty();
      assertThat(store.get(fooSegment, bytesOf(3))).contains(bytesOf(3));

      assertThat(store.get(barSegment, bytesOf(4))).contains(bytesOf(4));
      assertThat(store.get(barSegment, bytesOf(5))).isEmpty();
      assertThat(store.get(barSegment, bytesOf(6))).isEmpty();

      store.close();
    }
  }

  @Test
  public void canGetThroughSegmentIteration() throws Exception {
    final SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store = createSegmentedStore();
    final RocksDbSegmentIdentifier fooSegment = store.getSegmentIdentifierByName(TestSegment.FOO);
    final RocksDbSegmentIdentifier barSegment = store.getSegmentIdentifierByName(TestSegment.BAR);

    final Transaction<RocksDbSegmentIdentifier> tx = store.startTransaction();
    tx.put(fooSegment, bytesOf(1), bytesOf(1));
    tx.put(fooSegment, bytesOf(2), bytesOf(2));
    tx.put(fooSegment, bytesOf(3), bytesOf(3));
    tx.put(barSegment, bytesOf(4), bytesOf(4));
    tx.put(barSegment, bytesOf(5), bytesOf(5));
    tx.put(barSegment, bytesOf(6), bytesOf(6));
    tx.commit();

    final Set<byte[]> gotFromFoo =
        store.getAllKeysThat(fooSegment, x -> Arrays.equals(x, bytesOf(3)));
    final Set<byte[]> gotFromBar =
        store.getAllKeysThat(
            barSegment, x -> Arrays.equals(x, bytesOf(4)) || Arrays.equals(x, bytesOf(5)));
    final Set<byte[]> gotEmpty =
        store.getAllKeysThat(fooSegment, x -> Arrays.equals(x, bytesOf(0)));

    assertThat(gotFromFoo.size()).isEqualTo(1);
    assertThat(gotFromBar.size()).isEqualTo(2);
    assertThat(gotEmpty).isEmpty();

    assertThat(gotFromFoo).containsExactlyInAnyOrder(bytesOf(3));
    assertThat(gotFromBar).containsExactlyInAnyOrder(bytesOf(4), bytesOf(5));

    store.close();
  }

  public enum TestSegment implements SegmentIdentifier {
    FOO(new byte[] {1}),
    BAR(new byte[] {2});

    private final byte[] id;
    private final String nameAsUtf8;

    TestSegment(final byte[] id) {
      this.id = id;
      this.nameAsUtf8 = new String(id, StandardCharsets.UTF_8);
    }

    @Override
    public String getName() {
      return nameAsUtf8;
    }

    @Override
    public byte[] getId() {
      return id;
    }
  }

  private SegmentedKeyValueStorage<RocksDbSegmentIdentifier> createSegmentedStore()
      throws Exception {
    return new RocksDBColumnarKeyValueStorage(
        new RocksDBConfigurationBuilder().databaseDir(folder.newFolder().toPath()).build(),
        Arrays.asList(TestSegment.FOO, TestSegment.BAR),
        new NoOpMetricsSystem(),
        RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);
  }

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return new SegmentedKeyValueStorageAdapter<>(TestSegment.FOO, createSegmentedStore());
  }
}
