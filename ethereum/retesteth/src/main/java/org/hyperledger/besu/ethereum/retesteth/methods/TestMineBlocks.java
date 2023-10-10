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
package org.hyperledger.besu.ethereum.retesteth.methods;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.blockcreation.PoWBlockCreator;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.retesteth.RetestethClock;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;

public class TestMineBlocks implements JsonRpcMethod {
  private final RetestethContext context;

  public TestMineBlocks(final RetestethContext context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return "test_mineBlocks";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    long blocksToMine = requestContext.getRequiredParameter(0, Long.class);
    while (blocksToMine-- > 0) {
      if (!mineNewBlock()) {
        return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), false);
      }
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), true);
  }

  private boolean mineNewBlock() {
    final RetestethClock retesethClock = context.getRetestethClock();
    final ProtocolSchedule protocolSchedule = context.getProtocolSchedule();
    final ProtocolContext protocolContext = context.getProtocolContext();
    final MutableBlockchain blockchain = context.getBlockchain();
    final HeaderValidationMode headerValidationMode = context.getHeaderValidationMode();
    final MiningParameters miningParameters = ImmutableMiningParameters.builder().build();
    miningParameters
        .getDynamic()
        .setTargetGasLimit(blockchain.getChainHeadHeader().getGasLimit())
        .setMinBlockOccupancyRatio(0.0)
        .setMinTransactionGasPrice(Wei.ZERO);
    final PoWBlockCreator blockCreator =
        new PoWBlockCreator(
            miningParameters,
            context.getCoinbase(),
            //            () -> Optional.of(blockchain.getChainHeadHeader().getGasLimit()),
            header -> context.getExtraData(),
            context.getTransactionPool(),
            protocolContext,
            protocolSchedule,
            context.getEthHashSolver(),
            //            Wei.ZERO,
            //            0.0,
            blockchain.getChainHeadHeader());
    final Block block =
        blockCreator.createBlock(retesethClock.instant().getEpochSecond()).getBlock();

    // advance clock so next mine won't hit the same timestamp
    retesethClock.advanceSeconds(1);

    final BlockImporter blockImporter =
        protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()).getBlockImporter();
    final BlockImportResult result =
        blockImporter.importBlock(
            protocolContext, block, headerValidationMode, headerValidationMode);
    return result.isImported();
  }
}
