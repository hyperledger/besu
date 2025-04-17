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

import org.hyperledger.besu.evm.log.Log;

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

  private static final int PUB_KEY_LENGTH = 48;
  private static final int WITHDRAWAL_CRED_LENGTH = 32;
  private static final int AMOUNT_LENGTH = 8;
  private static final int SIGNATURE_LENGTH = 96;
  private static final int INDEX_LENGTH = 8;

  // PublicKey is the first element.
  private static final int PUB_KEY_OFFSET = 0;
  // ABI encoding pads values to 32 bytes, so despite BLS public keys being length 48, the value
  // length here is 64. Then skip over the next length value.
  private static final int WITHDRAWAL_CRED_OFFSET =
      PUB_KEY_OFFSET + PUB_KEY_LENGTH + 16 + LENGTH_FIELD_SIZE;
  // WithdrawalCredentials is 32 bytes. Read that value then skip over next length.
  private static final int AMOUNT_OFFSET =
      WITHDRAWAL_CRED_OFFSET + WITHDRAWAL_CRED_LENGTH + LENGTH_FIELD_SIZE;
  // amount is only 8 bytes long but is stored in a 32 byte slot. The remaining 24 bytes need to be
  // added to the offset.
  private static final int SIGNATURE_OFFSET =
      AMOUNT_OFFSET + AMOUNT_LENGTH + 24 + LENGTH_FIELD_SIZE;
  // Signature is 96 bytes. Skip over it and the next length.
  private static final int INDEX_OFFSET = SIGNATURE_OFFSET + SIGNATURE_LENGTH + LENGTH_FIELD_SIZE;

  // The ABI encodes the position of dynamic elements first. Since there are 5
  // elements, skip over the positional data. The first 32 bytes of dynamic
  // elements also encode their actual length. Skip over that value too.
  private static final int DATA_START_POSITION = 32 * 5 + LENGTH_FIELD_SIZE;

  public static Bytes decodeFromLog(final Log log) {
    final Bytes data = log.getData();

    if (data.size() != DEPOSIT_LOG_LENGTH) {
      throw new IllegalArgumentException(
          "Invalid deposit log length. Must be "
              + DEPOSIT_LOG_LENGTH
              + " bytes, but is "
              + data.size()
              + " bytes");
    }

    final Bytes pubKey = data.slice(DATA_START_POSITION + PUB_KEY_OFFSET, PUB_KEY_LENGTH);
    final Bytes withdrawalCred =
        data.slice(DATA_START_POSITION + WITHDRAWAL_CRED_OFFSET, WITHDRAWAL_CRED_LENGTH);
    final Bytes amount = data.slice(DATA_START_POSITION + AMOUNT_OFFSET, AMOUNT_LENGTH);
    final Bytes signature = data.slice(DATA_START_POSITION + SIGNATURE_OFFSET, SIGNATURE_LENGTH);
    final Bytes index = data.slice(DATA_START_POSITION + INDEX_OFFSET, INDEX_LENGTH);

    return Bytes.concatenate(pubKey, withdrawalCred, amount, signature, index);
  }
}
