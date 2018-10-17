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
package tech.pegasys.pantheon.ethereum.rlp;

import java.nio.ByteBuffer;

public final class RlpUtils {

  public static final int RLP_ZERO = 0x80;

  private RlpUtils() {
    // Utility Class
  }

  public static int encode(final byte[] buffer, final long value) {
    if (value == 0L) {
      buffer[0] = (byte) RLP_ZERO;
      return 1;
    } else {
      final int resultBytes = 8 - Long.numberOfLeadingZeros(value) / 8;
      int shift = 0;
      for (int i = 0; i < resultBytes; i++) {
        buffer[resultBytes - i - 1] = (byte) (value >> shift & 0xFF);
        shift += 8;
      }
      return resultBytes;
    }
  }

  /**
   * Decodes the offset of an RLP encoded element.
   *
   * @param buffer Buffer that has an RLP element starting at index {@code start}
   * @param start the index into the bytebuffer from which to start decoding.
   * @return Length of the RLP element
   */
  public static int decodeOffset(final ByteBuffer buffer, final int start) {
    int len = 0;
    final int offset;
    final int first = buffer.get(start) & 0xff;
    if (first <= 0x7f) {
      offset = 0;
    } else if (first <= 0xb7) {
      offset = 1;
    } else if (first <= 0xbf) {
      final int lenOfLen = first - 0xb7;
      for (int i = 0; i < lenOfLen; ++i) {
        len = (len << 8) + (buffer.get(start + 1 + i) & 0xff);
      }
      offset = 1 + lenOfLen;
    } else if (first < 0xf8) {
      offset = 1;
    } else {
      final int lenOfLen = first - 0xf7;
      for (int i = 0; i < lenOfLen; ++i) {
        len = (len << 8) + (buffer.get(start + 1 + i) & 0xff);
      }
      offset = 1 + lenOfLen;
    }
    return offset;
  }

  /**
   * Decodes the offset of an RLP encoded element. TODO: Don't wrap in buffer, use array directly
   *
   * @param buffer Buffer that has an RLP element starting at index {@code start}
   * @param start the index into buffer from which to start decoding.
   * @return Length of the RLP element
   */
  public static int decodeOffset(final byte[] buffer, final int start) {
    return decodeOffset(ByteBuffer.wrap(buffer), start);
  }

  /**
   * Decodes the length of an RLP encoded element starting at the beginning of the given buffer.
   *
   * @param buffer Buffer that has an RLP element starting at index {@code start}
   * @param start the index into buffer from which to determine the length of an RLP element.
   * @return Length of the RLP element
   */
  public static int decodeLength(final ByteBuffer buffer, final int start) {
    int len = 0;
    final int offset;
    final int first = buffer.get(start) & 0xff;
    if (first <= 0x7f) {
      len = 1;
      offset = 0;
    } else if (first <= 0xb7) {
      len = first - RLP_ZERO;
      offset = 1;
    } else if (first <= 0xbf) {
      final int lenOfLen = first - 0xb7;
      for (int i = 0; i < lenOfLen; ++i) {
        len = (len << 8) + (buffer.get(start + 1 + i) & 0xff);
      }
      offset = 1 + lenOfLen;
    } else if (first < 0xf8) {
      len = first - 0xc0;
      offset = 1;
    } else {
      final int lenOfLen = first - 0xf7;
      for (int i = 0; i < lenOfLen; ++i) {
        len = (len << 8) + (buffer.get(start + 1 + i) & 0xff);
      }
      offset = 1 + lenOfLen;
    }
    return len + offset;
  }

  /**
   * Decodes the length of an RLP encoded element starting at the beginning of the given buffer.
   *
   * <p>TODO: Don't wrap in buffer, use array directly
   *
   * @param buffer Buffer that has an RLP element starting at index {@code start}
   * @param start the index into buffer from which to start decoding an RLP element length.
   * @return Length of the RLP element
   */
  public static int decodeLength(final byte[] buffer, final int start) {
    return decodeLength(ByteBuffer.wrap(buffer), start);
  }

  public static long readLong(final int offset, final int length, final byte[] buffer) {
    long num = 0;
    for (int i = decodeOffset(buffer, offset); i < length; ++i) {
      num = (num << 8) + (buffer[offset + i] & 0xff);
    }
    return num;
  }

  public static int nextOffset(final byte[] buffer, final int offset) {
    return offset + decodeLength(buffer, offset);
  }
}
