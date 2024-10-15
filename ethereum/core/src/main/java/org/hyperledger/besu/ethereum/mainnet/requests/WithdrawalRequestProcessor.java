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
import org.hyperledger.besu.datatypes.RequestType;
import org.hyperledger.besu.ethereum.core.Request;

import org.apache.tuweni.bytes.Bytes;

/** Processor for handling withdrawal requests. */
public class WithdrawalRequestProcessor extends AbstractSystemCallRequestProcessor<Request> {

  public static final Address DEFAULT_WITHDRAWAL_REQUEST_CONTRACT_ADDRESS =
      Address.fromHexString("0x00A3ca265EBcb825B45F985A16CEFB49958cE017");

  private final Address withdrawalRequestContractAddress;

  public WithdrawalRequestProcessor(final Address withdrawalRequestContractAddress) {
    this.withdrawalRequestContractAddress = withdrawalRequestContractAddress;
  }

  /**
   * Gets the call address for withdrawal requests.
   *
   * @return The call address.
   */
  @Override
  protected Address getCallAddress() {
    return withdrawalRequestContractAddress;
  }

  /**
   * Parses a single withdrawal request from the provided bytes.
   *
   * @param requestBytes The bytes representing a single withdrawal request.
   * @return A parsed {@link Request} object.
   */
  @Override
  protected Request parseRequest(final Bytes requestBytes) {
    return new Request(RequestType.WITHDRAWAL, requestBytes);
  }
}
