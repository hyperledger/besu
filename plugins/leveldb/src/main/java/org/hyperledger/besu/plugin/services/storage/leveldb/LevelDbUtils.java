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

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.Arrays;

class LevelDbUtils {

  static byte[] getKeyAfterColumn(final SegmentIdentifier column) {
    final byte[] keyAfterColumn = column.getId();
    keyAfterColumn[keyAfterColumn.length - 1]++;
    return keyAfterColumn;
  }

  static <K, V> byte[] getColumnKey(final SegmentIdentifier column, final byte[] key) {
    final byte[] prefix = column.getId();
    return concat(prefix, key);
  }

  static <K, V> boolean isFromColumn(final SegmentIdentifier column, final byte[] key) {
    final byte[] prefix = column.getId();
    if (key.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (prefix[i] != key[i]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] concat(final byte[] a, final byte[] b) {
    final byte[] result = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

  static byte[] removeKeyPrefix(final SegmentIdentifier column, final byte[] key) {
    return Arrays.copyOfRange(key, column.getId().length, key.length);
  }
}
