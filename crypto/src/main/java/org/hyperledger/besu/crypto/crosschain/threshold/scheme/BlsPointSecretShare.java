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
package org.hyperledger.besu.crypto.crosschain.threshold.scheme;

import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;

import java.math.BigInteger;

/** Represents a single share. */
public class BlsPointSecretShare {
  private BlsPoint shareY;
  private BigInteger shareX;

  public BlsPointSecretShare(final BigInteger shareX, final BlsPoint shareY) {
    this.shareX = shareX;
    this.shareY = shareY;
  }

  public BlsPoint getShareY() {
    return shareY;
  }

  public BigInteger getShareX() {
    return shareX;
  }

  public void updateY(final BlsPoint y) {
    this.shareY = y;
  }

  //    private static final int INT_LEN = 4;
  //
  //    /**
  //     * Serialise the x and y values.
  //     *
  //     * @return Serialised x and y values.
  //     */
  //    public byte[] toBytes() {
  //        byte[] shareXBytes = this.shareX.toByteArray();
  //        int lenShareXBytes = shareXBytes.length;
  //        byte[] lenShareXBytesBytes =
  // ByteBuffer.allocate(INT_LEN).putInt(lenShareXBytes).array();
  //
  //        byte[] shareYBytes = this.shareY.toByteArray();
  //        int lenShareYBytes = shareYBytes.length;
  //        byte[] lenShareYBytesBytes =
  // ByteBuffer.allocate(INT_LEN).putInt(lenShareYBytes).array();
  //
  //        int totalLen = lenShareXBytesBytes.length + shareXBytes.length +
  // lenShareYBytesBytes.length + shareYBytes.length;
  //        byte[] result = new byte[totalLen];
  //
  //        int offset = Arrays.copyInto(lenShareXBytesBytes, result, 0);
  //        offset = Arrays.copyInto(shareXBytes, result, offset);
  //        offset = Arrays.copyInto(lenShareYBytesBytes, result, offset);
  //        offset = Arrays.copyInto(shareYBytes, result, offset);
  //
  //        if (offset != totalLen) {
  //            throw new RuntimeException("Didn't copy all data into array");
  //        }
  //        return result;
  //    }
  //
  //
  //    /**
  //     * Deserialise the x and y values.
  //     *
  //     * @param from Serialised x and y values.
  //     */
  //    public static BlsPointSecretShare fromBytes(byte[] from) {
  //        int offset = 0;
  //        byte[] lenShareXBytesBytes = new byte[INT_LEN];
  //        offset = Arrays.copyFrom(from, lenShareXBytesBytes, offset);
  //        int lenShareXBytes = ByteBuffer.wrap(lenShareXBytesBytes).getInt();
  //
  //        byte[] shareXBytes = new byte[lenShareXBytes];
  //        offset = Arrays.copyFrom(from, shareXBytes, offset);
  //        BigInteger shareX = new BigInteger(1, shareXBytes);
  //
  //        byte[] lenShareYBytesBytes = new byte[INT_LEN];
  //        offset = Arrays.copyFrom(from, lenShareYBytesBytes, offset);
  //        int lenShareYBytes = ByteBuffer.wrap(lenShareYBytesBytes).getInt();
  //
  //        byte[] shareYBytes = new byte[lenShareYBytes];
  //        offset = Arrays.copyFrom(from, shareYBytes, offset);
  //        BigInteger shareY = new BigInteger(1, shareYBytes);
  //
  //        return new BlsPointSecretShare(shareX, shareY);
  //    }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    return builder
        .append(", IntegerSecretShare: X: " + this.shareX)
        .append(", IntegerSecretShare: Y: " + this.shareY)
        .append("\n")
        .toString();
  }
}
