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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import org.apache.tuweni.bytes.Bytes;

public class PrivGetCode extends AbstractBlockParameterMethod {

  private final PrivacyController privacyController;
  private final EnclavePublicKeyProvider enclavePublicKeyProvider;

  public PrivGetCode(
      final BlockchainQueries blockchainQueries,
      final PrivacyController privacyController,
      final EnclavePublicKeyProvider enclavePublicKeyProvider) {
    super(blockchainQueries);
    this.privacyController = privacyController;
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_GET_CODE.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(2, BlockParameter.class);
  }

  @Override
  protected String resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final String privacyGroupId = request.getRequiredParameter(0, String.class);
    final Address address = request.getRequiredParameter(1, Address.class);

    final String enclavePublicKey = enclavePublicKeyProvider.getEnclaveKey(request.getUser());

    return getBlockchainQueries()
        .getBlockHashByNumber(blockNumber)
        .flatMap(
            blockHash ->
                privacyController.getContractCode(
                    privacyGroupId, address, blockHash, enclavePublicKey))
        .map(Bytes::toString)
        .orElse(null);
  }
}
