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

import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class Payload {
  private final Bytes payloadBytes;

  private Long zeroBytes = null;

  public Payload(final Bytes payloadBytes) {
    this.payloadBytes = payloadBytes;
  }

  public Bytes getPayloadBytes() {
    return payloadBytes;
  }

  public long getZeroBytesCount() {
    if (payloadBytes == null) {
      return 0L;
    }

    if (zeroBytes == null) {
      zeroBytes = computeZeroBytes();
    }
    return zeroBytes;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Payload payload = (Payload) o;
    return Objects.equals(payloadBytes, payload.payloadBytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payloadBytes);
  }

  @Override
  public String toString() {
    return "Payload{" + "payloadBytes=" + payloadBytes + '}';
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
