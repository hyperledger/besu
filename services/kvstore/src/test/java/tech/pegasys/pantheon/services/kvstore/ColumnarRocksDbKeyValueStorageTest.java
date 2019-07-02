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
package tech.pegasys.pantheon.services.kvstore;

import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage.Segment;
import tech.pegasys.pantheon.services.kvstore.SegmentedKeyValueStorage.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;

public class ColumnarRocksDbKeyValueStorageTest extends AbstractKeyValueStorageTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void twoSegmentsAreIndependent() throws Exception {
    final SegmentedKeyValueStorage<ColumnFamilyHandle> store = createSegmentedStore();

    Transaction<ColumnFamilyHandle> tx = store.startTransaction();
    tx.put(
        store.getSegmentIdentifierByName(TestSegment.BAR),
        BytesValue.fromHexString("0001"),
        BytesValue.fromHexString("0FFF"));
    tx.commit();
    final Optional<BytesValue> result =
        store.get(
            store.getSegmentIdentifierByName(TestSegment.FOO), BytesValue.fromHexString("0001"));
    assertEquals(Optional.empty(), result);
  }

  @Test
  public void canRemoveThroughSegmentIteration() throws Exception {
    final SegmentedKeyValueStorage<ColumnFamilyHandle> store = createSegmentedStore();
    final ColumnFamilyHandle fooSegment = store.getSegmentIdentifierByName(TestSegment.FOO);
    final ColumnFamilyHandle barSegment = store.getSegmentIdentifierByName(TestSegment.BAR);

    Transaction<ColumnFamilyHandle> tx = store.startTransaction();
    tx.put(fooSegment, BytesValue.of(1), BytesValue.of(1));
    tx.put(fooSegment, BytesValue.of(2), BytesValue.of(2));
    tx.put(fooSegment, BytesValue.of(3), BytesValue.of(3));
    tx.put(barSegment, BytesValue.of(4), BytesValue.of(4));
    tx.put(barSegment, BytesValue.of(5), BytesValue.of(5));
    tx.put(barSegment, BytesValue.of(6), BytesValue.of(6));
    tx.commit();

    final long removedFromFoo = store.removeUnless(fooSegment, x -> x.equals(BytesValue.of(3)));
    final long removedFromBar = store.removeUnless(barSegment, x -> x.equals(BytesValue.of(4)));

    assertEquals(2, removedFromFoo);
    assertEquals(2, removedFromBar);

    assertEquals(Optional.empty(), store.get(fooSegment, BytesValue.of(1)));
    assertEquals(Optional.empty(), store.get(fooSegment, BytesValue.of(2)));
    assertEquals(Optional.of(BytesValue.of(3)), store.get(fooSegment, BytesValue.of(3)));

    assertEquals(Optional.of(BytesValue.of(4)), store.get(barSegment, BytesValue.of(4)));
    assertEquals(Optional.empty(), store.get(barSegment, BytesValue.of(5)));
    assertEquals(Optional.empty(), store.get(barSegment, BytesValue.of(6)));
  }

  public enum TestSegment implements Segment {
    FOO(new byte[] {1}),
    BAR(new byte[] {2});

    private final byte[] id;

    TestSegment(final byte[] id) {
      this.id = id;
    }

    @Override
    public String getName() {
      return name();
    }

    @Override
    public byte[] getId() {
      return id;
    }
  }

  private SegmentedKeyValueStorage<ColumnFamilyHandle> createSegmentedStore() throws Exception {
    return ColumnarRocksDbKeyValueStorage.create(
        RocksDbConfiguration.builder().databaseDir(folder.newFolder().toPath()).build(),
        Arrays.asList(TestSegment.FOO, TestSegment.BAR),
        new NoOpMetricsSystem());
  }

  @Override
  protected KeyValueStorage createStore() throws Exception {
    return new SegmentedKeyValueStorageAdapter<>(TestSegment.FOO, createSegmentedStore());
  }
}
