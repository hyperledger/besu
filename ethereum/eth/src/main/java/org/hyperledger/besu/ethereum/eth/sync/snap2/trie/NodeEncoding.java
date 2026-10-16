/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.snap2.trie;

import org.hyperledger.besu.datatypes.Hash;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Allocation-light RLP encoding of Merkle Patricia trie nodes over 32-byte keys. */
final class NodeEncoding {

  /** Encoding of an empty child or of an empty branch value. */
  private static final byte EMPTY = (byte) 0x80;

  private static final byte HASH_PREFIX = (byte) 0xa0;

  private NodeEncoding() {}

  /** Returns nibble {@code i} of a 32-byte key. */
  public static int nibble(final byte[] key, final int i) {
    final int b = key[i >> 1];
    return (i & 1) == 0 ? (b >> 4) & 0x0f : b & 0x0f;
  }

  /** Returns the number of leading nibbles shared by two 32-byte keys. */
  public static int commonPrefix(final byte[] a, final byte[] b) {
    for (int i = 0; i < 32; i++) {
      final int x = (a[i] ^ b[i]) & 0xff;
      if (x != 0) {
        return 2 * i + ((x & 0xf0) == 0 ? 1 : 0);
      }
    }
    return 64;
  }

  /** Returns the first {@code depth} nibbles of a key as a location, one nibble per byte. */
  public static Bytes location(final byte[] key, final int depth) {
    final byte[] out = new byte[depth];
    for (int i = 0; i < depth; i++) {
      out[i] = (byte) nibble(key, i);
    }
    return Bytes.wrap(out);
  }

  /** Packs a location (one nibble per byte) into a 32-byte key padded with zero nibbles. */
  public static byte[] padLocation(final Bytes location) {
    final byte[] out = new byte[32];
    for (int i = 0; i < location.size(); i++) {
      final int n = location.get(i) & 0x0f;
      out[i >> 1] |= (byte) ((i & 1) == 0 ? n << 4 : n);
    }
    return out;
  }

  /** Returns the keccak hash of the given bytes. */
  public static Bytes32 keccak(final Bytes bytes) {
    return Bytes32.wrap(Hash.hash(bytes).getBytes());
  }

  /** Encodes the reference to a hashed node. */
  public static Bytes hashRef(final Bytes32 hash) {
    final byte[] out = new byte[33];
    out[0] = HASH_PREFIX;
    System.arraycopy(hash.toArrayUnsafe(), 0, out, 1, 32);
    return Bytes.wrap(out);
  }

  /** Returns whether a reference is a hash reference rather than an inlined node. */
  public static boolean isHashRef(final Bytes ref) {
    return ref.size() == 33 && ref.get(0) == HASH_PREFIX;
  }

  /** Returns the hash of the node behind a reference. */
  public static Bytes32 refHash(final Bytes ref) {
    return isHashRef(ref) ? Bytes32.wrap(ref.slice(1, 32)) : keccak(ref);
  }

  /** Encodes a branch node without value; null children are empty. */
  public static Bytes branch(final Bytes[] children) {
    int payload = 1;
    for (final Bytes child : children) {
      payload += child == null ? 1 : child.size();
    }
    final byte[] out = new byte[listHeaderSize(payload) + payload];
    int pos = writeListHeader(out, payload);
    for (final Bytes child : children) {
      if (child == null) {
        out[pos++] = EMPTY;
      } else {
        pos = copy(child, out, pos);
      }
    }
    out[pos] = EMPTY;
    return Bytes.wrap(out);
  }

  /** Encodes a leaf holding the nibbles of {@code key} from {@code from} and an RLP value. */
  public static Bytes leaf(final byte[] key, final int from, final Bytes value) {
    final int pathSize = pathStringSize(64 - from);
    final int valueSize = stringSize(value);
    final int payload = pathSize + valueSize;
    final byte[] out = new byte[listHeaderSize(payload) + payload];
    int pos = writeListHeader(out, payload);
    pos = writePath(out, pos, key, from, 64, true);
    writeString(out, pos, value);
    return Bytes.wrap(out);
  }

  /** Encodes an extension over the nibbles of {@code key} in {@code [from, to)}. */
  public static Bytes extension(
      final byte[] key, final int from, final int to, final Bytes childRef) {
    final int payload = pathStringSize(to - from) + childRef.size();
    final byte[] out = new byte[listHeaderSize(payload) + payload];
    int pos = writeListHeader(out, payload);
    pos = writePath(out, pos, key, from, to, false);
    copy(childRef, out, pos);
    return Bytes.wrap(out);
  }

  private static int pathStringSize(final int nibbles) {
    final int hpSize = nibbles / 2 + 1;
    // a single hex-prefix byte is always below 0x80 and encodes as itself
    return hpSize == 1 ? 1 : 1 + hpSize;
  }

  private static int writePath(
      final byte[] out,
      final int start,
      final byte[] key,
      final int from,
      final int to,
      final boolean leaf) {
    final int nibbles = to - from;
    final int hpSize = nibbles / 2 + 1;
    int pos = start;
    if (hpSize > 1) {
      out[pos++] = (byte) (0x80 + hpSize);
    }
    final int flag = leaf ? 2 : 0;
    int i = from;
    if ((nibbles & 1) == 1) {
      out[pos++] = (byte) (((flag | 1) << 4) | nibble(key, i));
      i++;
    } else {
      out[pos++] = (byte) (flag << 4);
    }
    for (; i < to; i += 2) {
      out[pos++] = (byte) ((nibble(key, i) << 4) | nibble(key, i + 1));
    }
    return pos;
  }

  private static int stringSize(final Bytes value) {
    final int size = value.size();
    if (size == 1 && (value.get(0) & 0xff) < 0x80) {
      return 1;
    }
    return size < 56 ? 1 + size : 1 + lengthOfLength(size) + size;
  }

  private static int writeString(final byte[] out, final int start, final Bytes value) {
    final int size = value.size();
    int pos = start;
    if (size == 1 && (value.get(0) & 0xff) < 0x80) {
      out[pos++] = value.get(0);
      return pos;
    }
    if (size < 56) {
      out[pos++] = (byte) (0x80 + size);
    } else {
      final int lenOfLen = lengthOfLength(size);
      out[pos++] = (byte) (0xb7 + lenOfLen);
      pos = writeLength(out, pos, size, lenOfLen);
    }
    return copy(value, out, pos);
  }

  private static int listHeaderSize(final int payload) {
    return payload < 56 ? 1 : 1 + lengthOfLength(payload);
  }

  private static int writeListHeader(final byte[] out, final int payload) {
    if (payload < 56) {
      out[0] = (byte) (0xc0 + payload);
      return 1;
    }
    final int lenOfLen = lengthOfLength(payload);
    out[0] = (byte) (0xf7 + lenOfLen);
    return writeLength(out, 1, payload, lenOfLen);
  }

  private static int lengthOfLength(final int length) {
    return length < 0x100 ? 1 : length < 0x10000 ? 2 : length < 0x1000000 ? 3 : 4;
  }

  private static int writeLength(final byte[] out, final int start, final int length, final int n) {
    for (int i = n - 1; i >= 0; i--) {
      out[start + n - 1 - i] = (byte) (length >>> (8 * i));
    }
    return start + n;
  }

  private static int copy(final Bytes src, final byte[] out, final int pos) {
    final int size = src.size();
    System.arraycopy(src.toArrayUnsafe(), 0, out, pos, size);
    return pos + size;
  }
}
