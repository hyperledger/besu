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

public class ConsolidationRequestProcessor extends AbstractSystemCallRequestProcessor<Request> {
  public static final Address CONSOLIDATION_REQUEST_CONTRACT_ADDRESS =
      Address.fromHexString("0x01ABEA29659E5E97C95107F20BB753CD3E09BBBB");

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
   * Parses a single consolidation request from the provided bytes.
   *
   * @param requestBytes The bytes representing a single consolidation request.
   * @return A parsed {@link Request} object.
   */
  @Override
  protected Request parseRequest(final Bytes requestBytes) {
    return new Request(RequestType.CONSOLIDATION, requestBytes);
  }
}
