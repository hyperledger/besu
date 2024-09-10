/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public abstract class CompactEncoding {
  private CompactEncoding() {}

  public static final byte LEAF_TERMINATOR = 0x10;

  /**
   * Converts a byte sequence into a path by splitting each byte into two nibbles. The resulting
   * path is terminated with a leaf terminator.
   *
   * @param bytes the byte sequence to convert into a path
   * @return the resulting path
   */
  public static Bytes bytesToPath(final Bytes bytes) {
    final MutableBytes path = MutableBytes.create(bytes.size() * 2 + 1);
    int j = 0;
    for (int i = 0; i < bytes.size(); i += 1, j += 2) {
      final byte b = bytes.get(i);
      path.set(j, (byte) ((b >>> 4) & 0x0f));
      path.set(j + 1, (byte) (b & 0x0f));
    }
    path.set(j, LEAF_TERMINATOR);
    return path;
  }

  /**
   * Converts a path into a byte sequence by combining each pair of nibbles into a byte. The path
   * must be a leaf path, i.e., it must be terminated with a leaf terminator.
   *
   * @param path the path to convert into a byte sequence
   * @return the resulting byte sequence
   * @throws IllegalArgumentException if the path is empty or not a leaf path, or if it contains
   *     elements larger than a nibble
   */
  public static Bytes pathToBytes(final Bytes path) {
    checkArgument(!path.isEmpty(), "Path must not be empty");
    checkArgument(path.get(path.size() - 1) == LEAF_TERMINATOR, "Path must be a leaf path");
    final MutableBytes bytes = MutableBytes.create((path.size() - 1) / 2);
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

  /**
   * Encodes a path into a compact form. The encoding includes a metadata byte that indicates
   * whether the path is a leaf path and whether its length is odd or even.
   *
   * @param path the path to encode
   * @return the encoded path
   * @throws IllegalArgumentException if the path contains elements larger than a nibble
   */
  public static Bytes encode(final Bytes path) {
    int size = path.size();
    final boolean isLeaf = size > 0 && path.get(size - 1) == LEAF_TERMINATOR;
    if (isLeaf) {
      size = size - 1;
    }

    final MutableBytes encoded = MutableBytes.create((size + 2) / 2);
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

  /**
   * Decodes a path from its compact form. The decoding process takes into account the metadata byte
   * that indicates whether the path is a leaf path and whether its length is odd or even.
   *
   * @param encoded the encoded path to decode
   * @return the decoded path
   * @throws IllegalArgumentException if the encoded path is empty or its metadata byte is invalid
   */
  public static Bytes decode(final Bytes encoded) {
    final int size = encoded.size();
    checkArgument(size > 0);
    final byte metadata = encoded.get(0);
    checkArgument((metadata & 0xc0) == 0, "Invalid compact encoding");

    final boolean isLeaf = (metadata & 0x20) != 0;

    final int pathLength = ((size - 1) * 2) + (isLeaf ? 1 : 0);
    final MutableBytes path;
    int i = 0;

    if ((metadata & 0x10) != 0) {
      // need to use lower nibble of metadata
      path = MutableBytes.create(pathLength + 1);
      path.set(i++, (byte) (metadata & 0x0f));
    } else {
      path = MutableBytes.create(pathLength);
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
