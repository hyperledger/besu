/*
 * Copyright ConsenSys Software Inc., 2022
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

package org.hyperledger.besu.plugin.services.storage.leveldb;

import static org.hyperledger.besu.plugin.services.storage.leveldb.LevelDbUtils.isFromColumn;

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.iq80.leveldb.DBIterator;

public class LevelDbRawKeyIterator implements Iterator<byte[]> {

  private final LeveldbSegmentedKeyValueStorage dbInstance;
  private final DBIterator iterator;
  private final SegmentIdentifier segment;
  private final byte[] lastKey;

  public LevelDbRawKeyIterator(
      final LeveldbSegmentedKeyValueStorage dbInstance,
      final DBIterator iterator,
      final SegmentIdentifier segment,
      final byte[] lastKey) {
    this.dbInstance = dbInstance;
    this.iterator = iterator;
    this.segment = segment;
    this.lastKey = lastKey;
  }

  @Override
  public boolean hasNext() {
    synchronized (dbInstance) {
      dbInstance.assertOpen();
      return iterator.hasNext() && isValidKey();
    }
  }

  private boolean isValidKey() {
    final byte[] nextKey = iterator.peekNext().getKey();
    return isFromColumn(segment, nextKey) && Arrays.compareUnsigned(nextKey, lastKey) <= 0;
  }

  @Override
  public byte[] next() {
    synchronized (dbInstance) {
      dbInstance.assertOpen();
      return iterator.next().getKey();
    }
  }

  public Stream<byte[]> toStream() {
    final Spliterator<byte[]> split =
        Spliterators.spliteratorUnknownSize(
            this,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false);
  }
}
