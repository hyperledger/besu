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
package org.hyperledger.besu.datatypes;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** The enum Transaction type. */
public enum TransactionType {
  /** The Frontier. */
  FRONTIER(0xf8 /* this is serialized as 0x0 in TransactionCompleteResult */),
  /** Access list transaction type. */
  ACCESS_LIST(0x01),
  /** Eip1559 transaction type. */
  EIP1559(0x02),
  /** Blob transaction type. */
  BLOB(0x03),
  /** Eip7702 transaction type. */
  DELEGATE_CODE(0x04);

  private static final Set<TransactionType> ACCESS_LIST_SUPPORTED_TRANSACTION_TYPES =
      Set.of(ACCESS_LIST, EIP1559, BLOB, DELEGATE_CODE);

  private static final EnumSet<TransactionType> LEGACY_FEE_MARKET_TRANSACTION_TYPES =
      EnumSet.of(TransactionType.FRONTIER, TransactionType.ACCESS_LIST);

  private final int typeValue;

  TransactionType(final int typeValue) {
    this.typeValue = typeValue;
  }

  /**
   * Gets serialized type.
   *
   * @return the serialized type
   */
  public byte getSerializedType() {
    return (byte) this.typeValue;
  }

  /**
   * Gets serialized type for returning in an eth transaction result, factoring in the special case
   * for FRONTIER transactions which have enum type 0xf8 but are represented as 0x00 in transaction
   * results.
   *
   * @return the serialized type
   */
  public byte getEthSerializedType() {
    return (this == FRONTIER ? 0x00 : this.getSerializedType());
  }

  /**
   * Compare to serialized type.
   *
   * @param b the byte value
   * @return the int result of comparison
   */
  public int compareTo(final Byte b) {
    return Byte.valueOf(getSerializedType()).compareTo(b);
  }

  /**
   * Convert TransactionType from int serialized type value.
   *
   * @param serializedTypeValue the serialized type value
   * @return the transaction type
   */
  public static TransactionType of(final int serializedTypeValue) {
    return Arrays.stream(
            new TransactionType[] {
              TransactionType.FRONTIER,
              TransactionType.ACCESS_LIST,
              TransactionType.EIP1559,
              TransactionType.BLOB,
              TransactionType.DELEGATE_CODE
            })
        .filter(transactionType -> transactionType.typeValue == serializedTypeValue)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Unsupported transaction type %x", serializedTypeValue)));
  }

  /**
   * Does transaction type support access list.
   *
   * @return the boolean
   */
  public boolean supportsAccessList() {
    return ACCESS_LIST_SUPPORTED_TRANSACTION_TYPES.contains(this);
  }

  /**
   * Does transaction type support EIP-1559 fee market.
   *
   * @return the boolean
   */
  public boolean supports1559FeeMarket() {
    return !LEGACY_FEE_MARKET_TRANSACTION_TYPES.contains(this);
  }

  /**
   * Does transaction type require chain id.
   *
   * @return the boolean
   */
  public boolean requiresChainId() {
    return !this.equals(FRONTIER);
  }

  /**
   * Does transaction type support data blobs.
   *
   * @return the boolean
   */
  public boolean supportsBlob() {
    return this.equals(BLOB);
  }

  /**
   * Does transaction type support delegate code.
   *
   * @return the boolean
   */
  public boolean supportsDelegateCode() {
    return this.equals(DELEGATE_CODE);
  }

  /**
   * Does transaction type require code.
   *
   * @return the boolean
   */
  public boolean requiresCodeDelegation() {
    return this.equals(DELEGATE_CODE);
  }
}
