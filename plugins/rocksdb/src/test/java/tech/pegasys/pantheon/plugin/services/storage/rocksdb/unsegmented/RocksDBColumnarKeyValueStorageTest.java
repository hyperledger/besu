/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugin.services.storage.rocksdb.unsegmented;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.kvstore.AbstractKeyValueStorageTest;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.SegmentIdentifier;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.segmented.RocksDBColumnarKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage.Transaction;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;

public class RocksDBColumnarKeyValueStorageTest extends AbstractKeyValueStorageTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void twoSegmentsAreIndependent() throws Exception {
    final SegmentedKeyValueStorage<ColumnFamilyHandle> store = createSegmentedStore();

    Transaction<ColumnFamilyHandle> tx = store.startTransaction();
    tx.put(
        store.getSegmentIdentifierByName(TestSegment.BAR),
        bytesFromHexString("0001"),
        bytesFromHexString("0FFF"));
    tx.commit();

    final Optional<byte[]> result =
        store.get(store.getSegmentIdentifierByName(TestSegment.FOO), bytesFromHexString("0001"));

    assertEquals(Optional.empty(), result);
  }

  @Test
  public void canRemoveThroughSegmentIteration() throws Exception {
    final SegmentedKeyValueStorage<ColumnFamilyHandle> store = createSegmentedStore();
    final ColumnFamilyHandle fooSegment = store.getSegmentIdentifierByName(TestSegment.FOO);
    final ColumnFamilyHandle barSegment = store.getSegmentIdentifierByName(TestSegment.BAR);

    Transaction<ColumnFamilyHandle> tx = store.startTransaction();
    tx.put(fooSegment, bytesOf(1), bytesOf(1));
    tx.put(fooSegment, bytesOf(2), bytesOf(2));
    tx.put(fooSegment, bytesOf(3), bytesOf(3));
    tx.put(barSegment, bytesOf(4), bytesOf(4));
    tx.put(barSegment, bytesOf(5), bytesOf(5));
    tx.put(barSegment, bytesOf(6), bytesOf(6));
    tx.commit();

    final long removedFromFoo = store.removeUnless(fooSegment, x -> Arrays.equals(x, bytesOf(3)));
    final long removedFromBar = store.removeUnless(barSegment, x -> Arrays.equals(x, bytesOf(4)));

    assertEquals(2, removedFromFoo);
    assertEquals(2, removedFromBar);

    assertEquals(Optional.empty(), store.get(fooSegment, bytesOf(1)));
    assertEquals(Optional.empty(), store.get(fooSegment, bytesOf(2)));
    assertArrayEquals(bytesOf(3), store.get(fooSegment, bytesOf(3)).get());

    assertArrayEquals(bytesOf(4), store.get(barSegment, bytesOf(4)).get());
    assertEquals(Optional.empty(), store.get(barSegment, bytesOf(5)));
    assertEquals(Optional.empty(), store.get(barSegment, bytesOf(6)));
  }

  public enum TestSegment implements SegmentIdentifier {
    FOO(new byte[] {1}),
    BAR(new byte[] {2});

    private final String nameAsUtf8;

    TestSegment(final byte[] id) {
      this.nameAsUtf8 = new String(id, StandardCharsets.UTF_8);
    }

    @Override
    public String getName() {
      return nameAsUtf8;
    }
  }

  private SegmentedKeyValueStorage<ColumnFamilyHandle> createSegmentedStore() throws Exception {
    return new RocksDBColumnarKeyValueStorage(
        new RocksDBConfigurationBuilder().databaseDir(folder.newFolder().toPath()).build(),
        Arrays.asList(TestSegment.FOO, TestSegment.BAR),
        new NoOpMetricsSystem());
  }

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return new SegmentedKeyValueStorageAdapter<>(TestSegment.FOO, createSegmentedStore());
  }
}
