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
package org.hyperledger.besu.ethereum.mainnet.requests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BLSPublicKey;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.ethereum.core.WithdrawalRequest;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

/** Processor for handling withdrawal requests. */
public class WithdrawalRequestProcessor
    extends AbstractSystemCallRequestProcessor<WithdrawalRequest> {

  public static final Address WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS =
      Address.fromHexString("0x00A3ca265EBcb825B45F985A16CEFB49958cE017");

  private static final int ADDRESS_BYTES = 20;
  private static final int PUBLIC_KEY_BYTES = 48;
  private static final int AMOUNT_BYTES = 8;
  private static final int WITHDRAWAL_REQUEST_BYTES_SIZE =
      ADDRESS_BYTES + PUBLIC_KEY_BYTES + AMOUNT_BYTES;

  /**
   * Gets the call address for withdrawal requests.
   *
   * @return The call address.
   */
  @Override
  protected Address getCallAddress() {
    return WITHDRAWAL_REQUEST_PREDEPLOY_ADDRESS;
  }

  /**
   * Gets the size of the bytes representing a single withdrawal request.
   *
   * @return The size of the bytes representing a single withdrawal request.
   */
  @Override
  protected int getRequestBytesSize() {
    return WITHDRAWAL_REQUEST_BYTES_SIZE;
  }

  /**
   * Parses a single withdrawal request from the provided bytes.
   *
   * @param requestBytes The bytes representing a single withdrawal request.
   * @return A parsed {@link WithdrawalRequest} object.
   */
  @Override
  protected WithdrawalRequest parseRequest(final Bytes requestBytes) {
    final Address sourceAddress = Address.wrap(requestBytes.slice(0, ADDRESS_BYTES));
    final BLSPublicKey validatorPublicKey =
        BLSPublicKey.wrap(requestBytes.slice(ADDRESS_BYTES, PUBLIC_KEY_BYTES));
    final UInt64 amount =
        UInt64.fromBytes(requestBytes.slice(ADDRESS_BYTES + PUBLIC_KEY_BYTES, AMOUNT_BYTES));
    return new WithdrawalRequest(sourceAddress, validatorPublicKey, GWei.of(amount));
  }
}
