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
package org.hyperledger.besu.crosschain.crypto.threshold.scheme;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/** Represents a single share. */
public class IntegerSecretShare {
  private BigInteger shareY;
  private BigInteger shareX;

  public IntegerSecretShare(final BigInteger shareX, final BigInteger shareY) {
    this.shareX = shareX;
    this.shareY = shareY;
  }

  public BigInteger getShareY() {
    return shareY;
  }

  public BigInteger getShareX() {
    return shareX;
  }

  public void updateY(final BigInteger y) {
    this.shareY = y;
  }

  private static final int INT_LEN = 4;

  /**
   * Serialise the x and y values.
   *
   * @return Serialised x and y values.
   */
  public byte[] toBytes() {
    byte[] shareXBytes = this.shareX.toByteArray();
    int lenShareXBytes = shareXBytes.length;
    byte[] lenShareXBytesBytes = ByteBuffer.allocate(INT_LEN).putInt(lenShareXBytes).array();

    byte[] shareYBytes = this.shareY.toByteArray();
    int lenShareYBytes = shareYBytes.length;
    byte[] lenShareYBytesBytes = ByteBuffer.allocate(INT_LEN).putInt(lenShareYBytes).array();

    int totalLen =
        lenShareXBytesBytes.length
            + shareXBytes.length
            + lenShareYBytesBytes.length
            + shareYBytes.length;
    byte[] result = new byte[totalLen];

    int offset = copyInto(lenShareXBytesBytes, result, 0);
    offset = copyInto(shareXBytes, result, offset);
    offset = copyInto(lenShareYBytesBytes, result, offset);
    offset = copyInto(shareYBytes, result, offset);

    if (offset != totalLen) {
      throw new RuntimeException("Didn't copy all data into array");
    }
    return result;
  }

  /**
   * Deserialise the x and y values.
   *
   * @param from Serialised x and y values.
   * @return object representing the X and Y values.
   */
  public static IntegerSecretShare fromBytes(final byte[] from) {
    int offset = 0;
    byte[] lenShareXBytesBytes = new byte[INT_LEN];
    offset = copyFrom(from, lenShareXBytesBytes, offset);
    int lenShareXBytes = ByteBuffer.wrap(lenShareXBytesBytes).getInt();

    byte[] shareXBytes = new byte[lenShareXBytes];
    offset = copyFrom(from, shareXBytes, offset);
    BigInteger shareX = new BigInteger(1, shareXBytes);

    byte[] lenShareYBytesBytes = new byte[INT_LEN];
    offset = copyFrom(from, lenShareYBytesBytes, offset);
    int lenShareYBytes = ByteBuffer.wrap(lenShareYBytesBytes).getInt();

    byte[] shareYBytes = new byte[lenShareYBytes];
    copyFrom(from, shareYBytes, offset);
    BigInteger shareY = new BigInteger(1, shareYBytes);

    return new IntegerSecretShare(shareX, shareY);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    return builder
        .append(", IntegerSecretShare: X: " + this.shareX)
        .append(", IntegerSecretShare: Y: " + this.shareY)
        .append("\n")
        .toString();
  }

  /**
   * Copy a byte array into another byte array.
   *
   * @param from Array to copy from.
   * @param to Array to copy to.
   * @param offset Offset within "to" array.
   * @return New offset.
   */
  private static int copyInto(final byte[] from, final byte[] to, final int offset) {
    System.arraycopy(from, 0, to, offset, from.length);
    return offset + from.length;
  }

  /**
   * Copy from an array at a certain offset into an array.
   *
   * @param from Array to copy from.
   * @param to Array to copy to. Offset to copy to is 0, and copy to.length bytes.
   * @param offset Offset within from array.
   * @return New offset.
   */
  private static int copyFrom(final byte[] from, final byte[] to, final int offset) {
    System.arraycopy(from, offset, to, 0, to.length);
    return offset + to.length;
  }
}
