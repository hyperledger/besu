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
package org.hyperledger.besu.ethereum.p2p.discovery.internal;

import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Arrays;

public class PeerDistanceCalculator {

  /**
   * Calculates the XOR distance between two values.
   *
   * @param v1 the first value
   * @param v2 the second value
   * @return the distance
   */
  static int distance(final BytesValue v1, final BytesValue v2) {
    assert (v1.size() == v2.size());
    final byte[] v1b = v1.extractArray();
    final byte[] v2b = v2.extractArray();
    if (Arrays.equals(v1b, v2b)) {
      return 0;
    }
    int distance = v1b.length * 8;
    for (int i = 0; i < v1b.length; i++) {
      final byte xor = (byte) (0xff & (v1b[i] ^ v2b[i]));
      if (xor == 0) {
        distance -= 8;
      } else {
        int p = 7;
        while (((xor >> p--) & 0x01) == 0) {
          distance--;
        }
        break;
      }
    }
    return distance;
  }
}
