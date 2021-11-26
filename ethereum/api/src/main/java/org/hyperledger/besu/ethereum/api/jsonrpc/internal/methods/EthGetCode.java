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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.hyperledger.besu.ethereum.goquorum.GoQuorumPrivateStateUtil.getPrivateWorldStateAtBlock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.account.Account;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;

public class EthGetCode extends AbstractBlockParameterOrBlockHashMethod {
  final Optional<PrivacyParameters> privacyParameters;

  public EthGetCode(
      final BlockchainQueries blockchainQueries,
      final Optional<PrivacyParameters> privacyParameters) {
    super(blockchainQueries);
    this.privacyParameters = privacyParameters;
  }

  public EthGetCode(
      final Supplier<BlockchainQueries> blockchainQueries,
      final Optional<PrivacyParameters> privacyParameters) {
    super(blockchainQueries);
    this.privacyParameters = privacyParameters;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_CODE.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    return request.getRequiredParameter(1, BlockParameterOrBlockHash.class);
  }

  @Override
  protected String resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    final Address address = request.getRequiredParameter(0, Address.class);
    if (privacyParameters.isPresent()
        && privacyParameters.get().getGoQuorumPrivacyParameters().isPresent()) {
      // get from private state if we can
      final Optional<BlockHeader> blockHeader =
          blockchainQueries.get().getBlockHeaderByHash(blockHash);
      if (blockHeader.isPresent()) {
        final MutableWorldState privateState =
            getPrivateWorldStateAtBlock(
                privacyParameters.get().getGoQuorumPrivacyParameters(), blockHeader.get());
        final Account privAccount = privateState.get(address);
        if (privAccount != null) {
          return privAccount.getCode().toHexString();
        }
      }
    }
    return getBlockchainQueries().getCode(address, blockHash).map(Bytes::toString).orElse(null);
  }
}
