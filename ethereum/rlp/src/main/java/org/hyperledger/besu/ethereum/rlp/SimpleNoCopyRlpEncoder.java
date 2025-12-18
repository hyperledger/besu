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
package org.hyperledger.besu.ethereum.rlp;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;

/**
 * A Simple RLP Encoder which follows the no-copy paradigm for minimising memory usage during sync
 */
public class SimpleNoCopyRlpEncoder {
  private static final int LENGTH_THRESHOLD = 55;

  private static final int MAXIMUM_STANDALONE_BYTE_VALUE = 127;
  private static final int SHORT_STRING_BYTE_PREFIX = 0x80;
  private static final int LONG_STRING_BYTE_PREFIX = 0xb7;
  private static final int SHORT_LIST_BYTE_PREFIX = 0xc0;
  private static final int LONG_LIST_BYTE_PREFIX = 0xf7;

  private static final Bytes[] SHORT_STRING_LENGTHS =
      new Bytes[LONG_STRING_BYTE_PREFIX - SHORT_STRING_BYTE_PREFIX + 1];
  private static final Bytes[] LONG_STRING_LENGTHS =
      new Bytes[SHORT_LIST_BYTE_PREFIX - LONG_STRING_BYTE_PREFIX];
  private static final Bytes[] SHORT_LIST_LENGTHS =
      new Bytes[LONG_LIST_BYTE_PREFIX - SHORT_LIST_BYTE_PREFIX + 1];
  private static final Bytes[] LONG_LIST_LENGTHS = new Bytes[0xff - LONG_LIST_BYTE_PREFIX + 1];

  static { // initialising these statically allows reuse of the Bytes objects, hopefully limiting
    // object creation and saving memory
    for (int i = 0; i < SHORT_STRING_LENGTHS.length; i++) {
      SHORT_STRING_LENGTHS[i] = Bytes.of((byte) (SHORT_STRING_BYTE_PREFIX + i));
    }
    for (int i = 0; i < LONG_STRING_LENGTHS.length; i++) {
      LONG_STRING_LENGTHS[i] = Bytes.of((byte) (LONG_STRING_BYTE_PREFIX + i));
    }
    for (int i = 0; i < SHORT_LIST_LENGTHS.length; i++) {
      SHORT_LIST_LENGTHS[i] = Bytes.of((byte) (SHORT_LIST_BYTE_PREFIX + i));
    }
    for (int i = 0; i < LONG_LIST_LENGTHS.length; i++) {
      LONG_LIST_LENGTHS[i] = Bytes.of((byte) (LONG_LIST_BYTE_PREFIX + i));
    }
  }

  /**
   * Encode the supplied bytes as an RLP string
   *
   * @param bytes the raw bytes to be encoded
   * @return he supplied bytes encoded as an RLP string
   */
  public Bytes encode(final Bytes bytes) {
    if (bytes.size() == 1 && Byte.toUnsignedInt(bytes.get(0)) <= MAXIMUM_STANDALONE_BYTE_VALUE) {
      return bytes;
    } else if (bytes.size() <= LENGTH_THRESHOLD) {
      return Bytes.concatenate(SHORT_STRING_LENGTHS[bytes.size()], bytes);
    } else { // bytes.size > LENGTH_THRESHOLD
      Bytes length = Bytes.minimalBytes(bytes.size());
      return Bytes.concatenate(LONG_STRING_LENGTHS[length.size()], length, bytes);
    }
  }

  /**
   * Encode the supplied list of RLP encoded items as an RLP list
   *
   * @param encodedBytesList the list of (already RLP encoded) items to be put into the RLP list
   * @return the supplied of RLP encoded items encoded as an RLP list
   */
  public Bytes encodeList(final List<Bytes> encodedBytesList) {
    int totalLength = encodedBytesList.stream().mapToInt(Bytes::size).sum();

    if (totalLength <= LENGTH_THRESHOLD) {
      return Bytes.concatenate(
          SHORT_LIST_LENGTHS[totalLength], Bytes.concatenate(encodedBytesList));
    } else { // totalLength > LENGTH_THRESHOLD
      Bytes length = Bytes.minimalBytes(totalLength);
      return Bytes.concatenate(
          LONG_LIST_LENGTHS[length.size()], length, Bytes.concatenate(encodedBytesList));
    }
  }
}
