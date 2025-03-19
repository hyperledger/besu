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
package org.hyperledger.besu.plugin.services.consensus.jsonrpc;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Qbft get validators by block hash. */
public class QbftGetValidatorsByBlockHash implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(QbftGetValidatorsByBlockHash.class);

  /** Placeholder */
  protected final Supplier<BlockchainService> blockchainServiceSupplier;

  private final BftService bftService;

  /**
   * Instantiates a new Qbft get validators by block hash.
   *
   * @param blockchain the blockchain
   * @param bftService the BFT plugin service
   */
  public QbftGetValidatorsByBlockHash(
      final BlockchainService blockchain, final BftService bftService) {
    blockchainServiceSupplier = Suppliers.ofInstance(blockchain);
    this.bftService = bftService;
  }

  @Override
  public String getName() {
    return RpcMethod.QBFT_GET_VALIDATORS_BY_BLOCK_HASH.getMethodName();
  }

  @Override
  public String response(final PluginRpcRequest requestContext) {
    final Hash hash;
    try {
      hash = new JsonRpcParameter().required(requestContext.getParams(), 0, Hash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block hash parameter (index 0)", RpcErrorType.INVALID_BLOCK_HASH_PARAMS, e);
    }
    LOG.trace("Received RPC rpcName={} blockHash={}", getName(), hash);
    final Optional<BlockContext> blockContext =
        blockchainServiceSupplier.get().getBlockByHash(hash);
    Object response =
        blockContext
            .map(
                block ->
                    bftService.getBftService().getSignersFrom(block.getBlockHeader()).stream()
                        .map(Address::toString)
                        .collect(Collectors.toList()))
            .orElse(null);

    if (response instanceof JsonRpcErrorResponse) {
      return ((JsonRpcErrorResponse) response).getErrorResponse();
    } else if (response == null) {
      return "null";
    }

    return response.toString();
  }
}
