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
import org.hyperledger.besu.ethereum.core.ConsolidationRequest;

import org.apache.tuweni.bytes.Bytes;

public class ConsolidationRequestProcessor
    extends AbstractSystemCallRequestProcessor<ConsolidationRequest> {
  public static final Address CONSOLIDATION_REQUEST_CONTRACT_ADDRESS =
      Address.fromHexString("0x00b42dbF2194e931E80326D950320f7d9Dbeac02");

  private static final int ADDRESS_BYTES = 20;
  private static final int PUBLIC_KEY_BYTES = 48;
  private static final int CONSOLIDATION_REQUEST_BYTES_SIZE =
      ADDRESS_BYTES + PUBLIC_KEY_BYTES + PUBLIC_KEY_BYTES;
  private final Address consolidationRequestContractAddress;

  public ConsolidationRequestProcessor(final Address consolidationRequestContractAddress) {
    this.consolidationRequestContractAddress = consolidationRequestContractAddress;
  }

  /**
   * Gets the call address for consolidation requests.
   *
   * @return The call address.
   */
  @Override
  protected Address getCallAddress() {
    return consolidationRequestContractAddress;
  }

  /**
   * Gets the size of the bytes representing a single consolidation request.
   *
   * @return The size of the bytes representing a single consolidation request.
   */
  @Override
  protected int getRequestBytesSize() {
    return CONSOLIDATION_REQUEST_BYTES_SIZE;
  }

  /**
   * Parses a single consolidation request from the provided bytes.
   *
   * @param requestBytes The bytes representing a single consolidation request.
   * @return A parsed {@link ConsolidationRequest} object.
   */
  @Override
  protected ConsolidationRequest parseRequest(final Bytes requestBytes) {
    final Address sourceAddress = Address.wrap(requestBytes.slice(0, ADDRESS_BYTES));
    final BLSPublicKey sourcePublicKey =
        BLSPublicKey.wrap(requestBytes.slice(ADDRESS_BYTES, PUBLIC_KEY_BYTES));
    final BLSPublicKey targetPublicKey =
        BLSPublicKey.wrap(requestBytes.slice(ADDRESS_BYTES + PUBLIC_KEY_BYTES, PUBLIC_KEY_BYTES));
    return new ConsolidationRequest(sourceAddress, sourcePublicKey, targetPublicKey);
  }
}
