/*
 * Copyright Besu Contributors.
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

public class PointAffine {

  final Element x;
  final Element y;

  public PointAffine(final Element x, final Element y) {
    this.x = x;
    this.y = y;
  }

  public static PointAffine fromProj(final Point point) {
    return new PointAffine(point.x.divide(point.z), point.y.divide(point.z));
  }
}
