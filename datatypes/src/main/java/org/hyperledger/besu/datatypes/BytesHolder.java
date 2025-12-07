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
package org.hyperledger.besu.datatypes;

import java.util.Arrays;
import java.util.Objects;

import org.apache.tuweni.bytes.Bytes;

public class BytesHolder implements Comparable<BytesHolder> {
  private final Bytes value;

  protected BytesHolder(final Bytes value) {
    this.value = value;
  }

  public Bytes getBytes() {
    return value;
  }

  public static BytesHolder createDefaultHolder(final Bytes value) {
    return new BytesHolder(value);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BytesHolder other)) {
      return false;
    }
    return Arrays.equals(value.toArrayUnsafe(), other.value.toArrayUnsafe());
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value.toString();
  }

  @Override
  public int compareTo(final BytesHolder bytesHolder) {
    Objects.requireNonNull(bytesHolder, "bytesHolder cannot be null");
    return value.compareTo(bytesHolder.value);
  }
}
