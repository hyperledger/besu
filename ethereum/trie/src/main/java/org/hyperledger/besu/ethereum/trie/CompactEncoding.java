/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.trie;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.MutableBytesValue;

abstract class CompactEncoding {
  private CompactEncoding() {}

  static final byte LEAF_TERMINATOR = 0x10;

  public static BytesValue bytesToPath(final BytesValue bytes) {
    final MutableBytesValue path = MutableBytesValue.create(bytes.size() * 2 + 1);
    int j = 0;
    for (int i = 0; i < bytes.size(); i += 1, j += 2) {
      final byte b = bytes.get(i);
      path.set(j, (byte) ((b >>> 4) & 0x0f));
      path.set(j + 1, (byte) (b & 0x0f));
    }
    path.set(j, LEAF_TERMINATOR);
    return path;
  }

  public static BytesValue pathToBytes(final BytesValue path) {
    checkArgument(!path.isEmpty(), "Path must not be empty");
    checkArgument(path.get(path.size() - 1) == LEAF_TERMINATOR, "Path must be a leaf path");
    final MutableBytesValue bytes = MutableBytesValue.create((path.size() - 1) / 2);
    int bytesPos = 0;
    for (int pathPos = 0; pathPos < path.size() - 1; pathPos += 2, bytesPos += 1) {
      final byte high = path.get(pathPos);
      final byte low = path.get(pathPos + 1);
      if ((high & 0xf0) != 0 || (low & 0xf0) != 0) {
        throw new IllegalArgumentException("Invalid path: contains elements larger than a nibble");
      }
      bytes.set(bytesPos, (byte) (high << 4 | low));
    }
    return bytes;
  }

  public static BytesValue encode(final BytesValue path) {
    int size = path.size();
    final boolean isLeaf = size > 0 && path.get(size - 1) == LEAF_TERMINATOR;
    if (isLeaf) {
      size = size - 1;
    }

    final MutableBytesValue encoded = MutableBytesValue.create((size + 2) / 2);
    int i = 0;
    int j = 0;

    if (size % 2 == 1) {
      // add first nibble to magic
      final byte high = (byte) (isLeaf ? 0x03 : 0x01);
      final byte low = path.get(i++);
      if ((low & 0xf0) != 0) {
        throw new IllegalArgumentException("Invalid path: contains elements larger than a nibble");
      }
      encoded.set(j++, (byte) (high << 4 | low));
    } else {
      final byte high = (byte) (isLeaf ? 0x02 : 0x00);
      encoded.set(j++, (byte) (high << 4));
    }

    while (i < size) {
      final byte high = path.get(i++);
      final byte low = path.get(i++);
      if ((high & 0xf0) != 0 || (low & 0xf0) != 0) {
        throw new IllegalArgumentException("Invalid path: contains elements larger than a nibble");
      }
      encoded.set(j++, (byte) (high << 4 | low));
    }

    return encoded;
  }

  public static BytesValue decode(final BytesValue encoded) {
    final int size = encoded.size();
    checkArgument(size > 0);
    final byte metadata = encoded.get(0);
    checkArgument((metadata & 0xc0) == 0, "Invalid compact encoding");

    final boolean isLeaf = (metadata & 0x20) != 0;

    final int pathLength = ((size - 1) * 2) + (isLeaf ? 1 : 0);
    final MutableBytesValue path;
    int i = 0;

    if ((metadata & 0x10) != 0) {
      // need to use lower nibble of metadata
      path = MutableBytesValue.create(pathLength + 1);
      path.set(i++, (byte) (metadata & 0x0f));
    } else {
      path = MutableBytesValue.create(pathLength);
    }

    for (int j = 1; j < size; j++) {
      final byte b = encoded.get(j);
      path.set(i++, (byte) ((b >>> 4) & 0x0f));
      path.set(i++, (byte) (b & 0x0f));
    }

    if (isLeaf) {
      path.set(i, LEAF_TERMINATOR);
    }

    return path;
  }
}
