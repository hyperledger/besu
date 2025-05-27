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
package org.hyperledger.besu.ethereum.core.encoding;

import org.hyperledger.besu.ethereum.mainnet.requests.InvalidDepositLogLayoutException;
import org.hyperledger.besu.evm.log.Log;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

/**
 * Decodes a deposit log into its constituent parts.
 *
 * <p>Deposit logs are emitted by the Ethereum 2.0 deposit contract when a validator deposits funds
 * into the contract.
 *
 * <p>The data of the deposit log is organized in 32 bytes chunks. The data layout is:
 *
 * <ol>
 *   <li>POSITION_PUB_KEY
 *   <li>POSITION_WITHDRAWAL_CRED
 *   <li>POSITION_AMOUNT
 *   <li>POSITION_SIGNATURE
 *   <li>POSITION_INDEX
 *   <li>LENGTH_PUB_KEY
 *   <li>PUB_KEY_DATA_1
 *   <li>PUB_KEY_DATA_2
 *   <li>LENGTH_WITHDRAWAL_CRED
 *   <li>WITHDRAWAL_CRED_DATA
 *   <li>LENGTH_AMOUNT
 *   <li>AMOUNT_DATA
 *   <li>LENGTH_SIGNATURE
 *   <li>SIGNATURE_DATA_1
 *   <li>SIGNATURE_DATA_2
 *   <li>LENGTH_INDEX
 *   <li>INDEX_DATA
 * </ol>
 *
 * We are only interested in the data fields and will skip over the position and length fields.
 */
public class DepositLogDecoder {

  private static final int DEPOSIT_LOG_LENGTH = 576;
  private static final int LENGTH_FIELD_SIZE = 32;

  private static final int POSITION_PUB_KEY = 0;
  private static final int POSITION_WITHDRAWAL_CRED = 32;
  private static final int POSITION_AMOUNT = 64;
  private static final int POSITION_SIGNATURE = 96;
  private static final int POSITION_INDEX = 128;

  private static final int PUB_KEY_LENGTH = 48;
  private static final int WITHDRAWAL_CRED_LENGTH = 32;
  private static final int AMOUNT_LENGTH = 8;
  private static final int SIGNATURE_LENGTH = 96;
  private static final int INDEX_LENGTH = 8;

  private static final int PUB_KEY_OFFSET = 160;
  private static final int WITHDRAWAL_CRED_OFFSET = 256;
  private static final int AMOUNT_OFFSET = 320;
  private static final int SIGNATURE_OFFSET = 384;
  private static final int INDEX_OFFSET = 512;

  public static Bytes decodeFromLog(final Log log) {
    final Bytes data = log.getData();
    validateLogLength(data);
    validateOffsets(data);
    validateSizes(data);

    final Bytes pubKey = extractField(data, PUB_KEY_OFFSET, PUB_KEY_LENGTH);
    final Bytes withdrawalCred = extractField(data, WITHDRAWAL_CRED_OFFSET, WITHDRAWAL_CRED_LENGTH);
    final Bytes amount = extractField(data, AMOUNT_OFFSET, AMOUNT_LENGTH);
    final Bytes signature = extractField(data, SIGNATURE_OFFSET, SIGNATURE_LENGTH);
    final Bytes index = extractField(data, INDEX_OFFSET, INDEX_LENGTH);

    return Bytes.concatenate(pubKey, withdrawalCred, amount, signature, index);
  }

  private static void validateLogLength(final Bytes data) {
    if (data.size() != DEPOSIT_LOG_LENGTH) {
      throw new InvalidDepositLogLayoutException(
          String.format(
              "Invalid deposit log length. Must be %d bytes, but is %d bytes",
              DEPOSIT_LOG_LENGTH, data.size()));
    }
  }

  private static void validateOffsets(final Bytes data) {
    validateFieldOffset(data, POSITION_PUB_KEY, PUB_KEY_OFFSET, "pubKey");
    validateFieldOffset(data, POSITION_WITHDRAWAL_CRED, WITHDRAWAL_CRED_OFFSET, "withdrawalCred");
    validateFieldOffset(data, POSITION_AMOUNT, AMOUNT_OFFSET, "amount");
    validateFieldOffset(data, POSITION_SIGNATURE, SIGNATURE_OFFSET, "signature");
    validateFieldOffset(data, POSITION_INDEX, INDEX_OFFSET, "index");
  }

  private static void validateFieldOffset(
      final Bytes data, final int position, final int expectedOffset, final String fieldName) {
    BigInteger offset = data.slice(position, LENGTH_FIELD_SIZE).toBigInteger();
    if (!offset.equals(BigInteger.valueOf(expectedOffset))) {
      throw new InvalidDepositLogLayoutException(
          String.format(
              "Invalid %s offset: expected %d, but got %d", fieldName, expectedOffset, offset));
    }
  }

  private static void validateSizes(final Bytes data) {
    validateFieldSize(data, PUB_KEY_OFFSET, PUB_KEY_LENGTH, "pubKey");
    validateFieldSize(data, WITHDRAWAL_CRED_OFFSET, WITHDRAWAL_CRED_LENGTH, "withdrawalCred");
    validateFieldSize(data, AMOUNT_OFFSET, AMOUNT_LENGTH, "amount");
    validateFieldSize(data, SIGNATURE_OFFSET, SIGNATURE_LENGTH, "signature");
    validateFieldSize(data, INDEX_OFFSET, INDEX_LENGTH, "index");
  }

  private static void validateFieldSize(
      final Bytes data, final int offset, final int expectedSize, final String fieldName) {
    BigInteger size = data.slice(offset, LENGTH_FIELD_SIZE).toBigInteger();
    if (!size.equals(BigInteger.valueOf(expectedSize))) {
      throw new InvalidDepositLogLayoutException(
          String.format("Invalid %s size: expected %d, but got %d", fieldName, expectedSize, size));
    }
  }

  private static Bytes extractField(final Bytes data, final int offset, final int length) {
    return data.slice(LENGTH_FIELD_SIZE + offset, length);
  }
}
