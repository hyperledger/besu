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
package org.hyperledger.besu.plugin.services.storage.rocksdb.segmented;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.hyperledger.besu.kvstore.AbstractKeyValueStorageTest;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDbSegmentIdentifier;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorage.Transaction;
import org.hyperledger.besu.services.kvstore.SnappableSegmentedKeyValueStorageAdapter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public abstract class RocksDBColumnarKeyValueStorageTest extends AbstractKeyValueStorageTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void assertClear() throws Exception {
    final byte[] key = bytesFromHexString("0001");
    final byte[] val1 = bytesFromHexString("0FFF");
    final byte[] val2 = bytesFromHexString("1337");
    final SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store = createSegmentedStore();
    RocksDbSegmentIdentifier segment = store.getSegmentIdentifierByName(TestSegment.FOO);
    KeyValueStorage duplicateSegmentRef =
        new SnappableSegmentedKeyValueStorageAdapter<>(TestSegment.FOO, store);

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

      store.stream(fooSegment)
          .map(Pair::getKey)
          .forEach(
              key -> {
                if (!Arrays.equals(key, bytesOf(3))) store.tryDelete(fooSegment, key);
              });
      store.stream(barSegment)
          .map(Pair::getKey)
          .forEach(
              key -> {
                if (!Arrays.equals(key, bytesOf(4))) store.tryDelete(barSegment, key);
              });

      for (final RocksDbSegmentIdentifier segment : Set.of(fooSegment, barSegment)) {
        assertThat(store.stream(segment).count()).isEqualTo(1);
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

  @Test
  public void dbShouldIgnoreExperimentalSegmentsIfNotExisted() throws Exception {
    final Path testPath = folder.newFolder().toPath();
    // Create new db should ignore experimental column family
    SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store =
        createSegmentedStore(
            testPath,
            Arrays.asList(TestSegment.FOO, TestSegment.BAR, TestSegment.EXPERIMENTAL),
            List.of(TestSegment.EXPERIMENTAL));
    store.close();

    // new db will be backward compatible with db without knowledge of experimental column family
    store =
        createSegmentedStore(testPath, Arrays.asList(TestSegment.FOO, TestSegment.BAR), List.of());
    store.close();
  }

  @Test
  public void dbShouldNotIgnoreExperimentalSegmentsIfExisted() throws Exception {
    final Path testPath = folder.newFolder().toPath();
    // Create new db with experimental column family
    SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store =
        createSegmentedStore(
            testPath,
            Arrays.asList(TestSegment.FOO, TestSegment.BAR, TestSegment.EXPERIMENTAL),
            List.of());
    store.close();

    // new db will not be backward compatible with db without knowledge of experimental column
    // family
    try {
      createSegmentedStore(testPath, Arrays.asList(TestSegment.FOO, TestSegment.BAR), List.of());
      fail("DB without knowledge of experimental column family should fail");
    } catch (StorageException e) {
      assertThat(e.getMessage()).contains("Column families not opened");
    }

    // Even if the column family is marked as ignored, as long as it exists, it will not be ignored
    // and the db opens normally
    store =
        createSegmentedStore(
            testPath,
            Arrays.asList(TestSegment.FOO, TestSegment.BAR, TestSegment.EXPERIMENTAL),
            List.of(TestSegment.EXPERIMENTAL));
    store.close();
  }

  @Test
  public void dbWillBeBackwardIncompatibleAfterExperimentalSegmentsAreAdded() throws Exception {
    final Path testPath = folder.newFolder().toPath();
    // Create new db should ignore experimental column family
    SegmentedKeyValueStorage<RocksDbSegmentIdentifier> store =
        createSegmentedStore(
            testPath,
            Arrays.asList(TestSegment.FOO, TestSegment.BAR, TestSegment.EXPERIMENTAL),
            List.of(TestSegment.EXPERIMENTAL));
    store.close();

    // new db will be backward compatible with db without knowledge of experimental column family
    store =
        createSegmentedStore(testPath, Arrays.asList(TestSegment.FOO, TestSegment.BAR), List.of());
    store.close();

    // Create new db without ignoring experimental colum family will add column to db
    store =
        createSegmentedStore(
            testPath,
            Arrays.asList(TestSegment.FOO, TestSegment.BAR, TestSegment.EXPERIMENTAL),
            List.of());
    store.close();

    // Now, the db will be backward incompatible with db without knowledge of experimental column
    // family
    try {
      createSegmentedStore(testPath, Arrays.asList(TestSegment.FOO, TestSegment.BAR), List.of());
      fail("DB without knowledge of experimental column family should fail");
    } catch (StorageException e) {
      assertThat(e.getMessage()).contains("Column families not opened");
    }
  }

  public enum TestSegment implements SegmentIdentifier {
    FOO(new byte[] {1}),
    BAR(new byte[] {2}),
    EXPERIMENTAL(new byte[] {3});

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

  protected abstract SegmentedKeyValueStorage<RocksDbSegmentIdentifier> createSegmentedStore()
      throws Exception;

  protected abstract SegmentedKeyValueStorage<RocksDbSegmentIdentifier> createSegmentedStore(
      final Path path,
      final List<SegmentIdentifier> segments,
      final List<SegmentIdentifier> ignorableSegments);

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return new SnappableSegmentedKeyValueStorageAdapter<>(TestSegment.FOO, createSegmentedStore());
  }
}
