/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.Optional;
import java.util.stream.Collectors;

/** QBFT getValidatorsByBlockNumber JSON/RPC call */
public class QbftGetValidatorsByBlockNumber extends AbstractBlockParameterMethod {

  private final BftService bftService;

  /**
   * Instantiates a new Qbft get validators by block number.
   *
   * @param blockchain the blockchain
   * @param bftService the bft service
   */
  public QbftGetValidatorsByBlockNumber(
      final BlockchainService blockchain, final BftService bftService) {
    super(blockchain);
    this.bftService = bftService;
  }

  @Override
  protected BlockParameter blockParameter(final PluginRpcRequest request) {
    try {
      return new JsonRpcParameter().required(request.getParams(), 0, BlockParameter.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters("Invalid block parameter (index 0): " + e.getMessage());
    }
  }

  @Override
  protected Object resultByBlockNumber(final PluginRpcRequest request, final long blockNumber) {
    final Optional<BlockContext> blockContext = getBlockchain().getBlockByNumber(blockNumber);
    // LOG.trace("Received RPC rpcName={} block={}", getName(), blockNumber);
    return blockContext
        .map(
            block ->
                bftService.getBftService().getSignersFrom(block.getBlockHeader()).stream()
                    .map(Address::toString)
                    .collect(Collectors.toList()))
        .orElse(null);
  }
}
