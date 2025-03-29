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
package org.hyperledger.besu.ethereum.core;

import org.apache.tuweni.bytes.Bytes;

class Payload {
  private final Bytes payloadBytes;

  private Long zeroBytes = null;

  Payload(final Bytes payloadBytes) {
    this.payloadBytes = payloadBytes;
  }

  Bytes getPayload() {
    return payloadBytes;
  }

  long zeroBytes() {
    if (payloadBytes == null) {
      return 0L;
    }

    if (zeroBytes == null) {
      zeroBytes = computeZeroBytes();
    }
    return zeroBytes;
  }

  private long computeZeroBytes() {
    int zeros = 0;
    for (int i = 0; i < payloadBytes.size(); i++) {
      if (payloadBytes.get(i) == 0) {
        zeros += 1;
      }
    }
    return zeros;
  }
}
