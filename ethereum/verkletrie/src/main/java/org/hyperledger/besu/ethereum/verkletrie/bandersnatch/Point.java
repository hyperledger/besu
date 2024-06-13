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
package org.hyperledger.besu.ethereum.verkletrie.bandersnatch;

import org.hyperledger.besu.ethereum.verkletrie.bandersnatch.fp.Element;

import java.nio.ByteOrder;

import org.apache.tuweni.bytes.Bytes32;

public class Point {

  public static final Point EMPTY = new Point(Element.ZERO, Element.ZERO, Element.ZERO);
  public static final Point IDENTITY = new Point(Element.ZERO, Element.ONE, Element.ONE);
  public final Element x;
  public final Element y;
  public final Element z;

  public Point(final Element x, final Element y, final Element z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  @Override
  public String toString() {
    return "Point{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
  }

  public Bytes32 mapToBaseFieldBytes() {
    Element res = x.divide(y);
    return res.getBytes(ByteOrder.LITTLE_ENDIAN);
  }

  public Bytes32 bytes() {
    PointAffine affineRepresentation = PointAffine.fromProj(this);
    Element x = affineRepresentation.x;
    if (!affineRepresentation.y.lexicographicallyLargest()) {
      x = x.neg();
    }
    return x.getBytes(ByteOrder.BIG_ENDIAN);
  }
}
