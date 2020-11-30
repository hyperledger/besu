/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.plugin.data;

import java.util.Set;

public enum TransactionType {
  FRONTIER(0xf8),
  EIP1559(0x3);

  private int typeValue;
  private static final Set<Integer> FRONTIER_COMPATIBILITY_TYPE_VALUES = Set.of(0xf9, 0xfa);

  TransactionType(final int typeValue) {
    this.typeValue = typeValue;
  }

  public int getSerializedType() {
    return this.typeValue;
  }

  static TransactionType of(int serializedTypeValue) {
    for (int frontierCompatibilityType : TransactionType.FRONTIER_COMPATIBILITY_TYPE_VALUES) {
      if (serializedTypeValue == frontierCompatibilityType) {
        return FRONTIER;
      }
    }
    for (TransactionType transactionType : TransactionType.values()) {
      if (transactionType.typeValue == serializedTypeValue) {
        return transactionType;
      }
    }
    throw new IllegalArgumentException(
        String.format("Unsupported transaction type %x", serializedTypeValue));
  }
}
