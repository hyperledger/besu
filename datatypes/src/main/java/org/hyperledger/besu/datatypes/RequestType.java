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
package org.hyperledger.besu.datatypes;

/** Enum representing different types of requests with associated serialized type values. */
public enum RequestType {
  /** DEPOSITS */
  DEPOSIT(0x00),
  /** WITHDRAWAL */
  WITHDRAWAL(0x01);

  private final int typeValue;

  RequestType(final int typeValue) {
    this.typeValue = typeValue;
  }

  /**
   * Gets the serialized type value of the request type.
   *
   * @return the serialized type value as a byte.
   */
  public byte getSerializedType() {
    return (byte) this.typeValue;
  }

  /**
   * Returns the {@link RequestType} corresponding to the given serialized type value.
   *
   * @param serializedTypeValue the serialized type value.
   * @return the corresponding {@link RequestType}.
   * @throws IllegalArgumentException if the serialized type value does not correspond to any {@link
   *     RequestType}.
   */
  public static RequestType of(final int serializedTypeValue) {
    return switch (serializedTypeValue) {
      case 0x00 -> DEPOSIT;
      case 0x01 -> WITHDRAWAL;
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported request type: 0x%02X", serializedTypeValue));
    };
  }
}
