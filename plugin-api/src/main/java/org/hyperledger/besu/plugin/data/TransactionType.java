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

import java.util.Arrays;

public enum TransactionType {
  FRONTIER(0xf8 /* doesn't end up being used as we don't serialize legacy txs with their type */),
  ACCESS_LIST(0x1),
  EIP1559(0xf /* placeholder value until we know what the real type byte will be */);

  private final int typeValue;

  TransactionType(final int typeValue) {
    this.typeValue = typeValue;
  }

  public int getSerializedType() {
    return this.typeValue;
  }

  public static TransactionType of(final int serializedTypeValue) {
    return Arrays.stream(TransactionType.values())
        .filter(transactionType -> transactionType.typeValue == serializedTypeValue)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Unsupported transaction type %x", serializedTypeValue)));
  }
}
