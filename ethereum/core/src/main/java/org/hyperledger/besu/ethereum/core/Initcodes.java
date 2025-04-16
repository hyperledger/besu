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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class Initcodes {
  private final List<Bytes> initcodeBytes;

  private Integer zeroBytes = null;
  private Integer totalSize = null;

  public Initcodes(final List<Bytes> initcodeBytes) {
    checkNotNull(initcodeBytes);
    this.initcodeBytes = initcodeBytes;
  }

  public Initcodes detatchedCopy() {
    return new Initcodes(initcodeBytes.stream().map(Bytes::copy).toList());
  }

  public List<Bytes> getInitcodeBytes() {
    return initcodeBytes;
  }

  public int getTotalSize() {
    if (totalSize == null) {
      totalSize = initcodeBytes.stream().mapToInt(Bytes::size).sum();
    }
    return totalSize;
  }

  public int getZeroBytesCount() {
    if (initcodeBytes == null || initcodeBytes.isEmpty()) {
      return 0;
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
    final Initcodes initcodes = (Initcodes) o;
    return Objects.equals(initcodeBytes, initcodes.initcodeBytes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(initcodeBytes);
  }

  @Override
  public String toString() {
    return "Payload{" + "initcodeBytes=" + initcodeBytes + '}';
  }

  private int computeZeroBytes() {
    int zeros = 0;
    for (final Bytes bytes : initcodeBytes) {
      for (byte b : bytes.toArrayUnsafe()) {
        if (b == 0) {
          zeros += 1;
        }
      }
    }
    return zeros;
  }

  public int count() {
    return initcodeBytes.size();
  }
}
