/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.retesteth.methods;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashBlockCreator;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.retesteth.RetestethClock;
import tech.pegasys.pantheon.ethereum.retesteth.RetestethContext;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.google.common.base.Functions;

public class TestMineBlocks implements JsonRpcMethod {
  private final RetestethContext context;
  private final JsonRpcParameter parameters;

  public TestMineBlocks(final RetestethContext context, final JsonRpcParameter parameters) {
    this.context = context;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "test_mineBlocks";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    long blocksToMine = parameters.required(request.getParams(), 0, Long.class);
    while (blocksToMine-- > 0) {
      if (!mineNewBlock()) {
        return new JsonRpcSuccessResponse(request.getId(), false);
      }
    }

    return new JsonRpcSuccessResponse(request.getId(), true);
  }

  private boolean mineNewBlock() {
    final RetestethClock retesethClock = context.getRetestethClock();
    final ProtocolSchedule<Void> protocolSchedule = context.getProtocolSchedule();
    final ProtocolContext<Void> protocolContext = context.getProtocolContext();
    final MutableBlockchain blockchain = context.getBlockchain();
    final HeaderValidationMode headerValidationMode = context.getHeaderValidationMode();
    final EthHashBlockCreator blockCreator =
        new EthHashBlockCreator(
            context.getCoinbase(),
            header -> BytesValue.of(),
            context.getTransactionPool().getPendingTransactions(),
            protocolContext,
            protocolSchedule,
            Functions.identity(),
            context.getEthHashSolver(),
            Wei.ZERO,
            blockchain.getChainHeadHeader());
    final Block block = blockCreator.createBlock(retesethClock.instant().getEpochSecond());

    // advance clock so next mine won't hit the same timestamp
    retesethClock.advanceSeconds(1);

    final BlockImporter<Void> blockImporter =
        protocolSchedule.getByBlockNumber(blockchain.getChainHeadBlockNumber()).getBlockImporter();
    return blockImporter.importBlock(
        protocolContext, block, headerValidationMode, headerValidationMode);
  }
}
