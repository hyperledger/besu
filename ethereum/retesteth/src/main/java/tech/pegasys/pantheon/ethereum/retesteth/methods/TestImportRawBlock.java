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
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.retesteth.RetestethContext;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestImportRawBlock implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();

  private static final String METHOD_NAME = "test_importRawBlock";

  private final RetestethContext context;
  private final JsonRpcParameter parameters;

  public TestImportRawBlock(final RetestethContext context, final JsonRpcParameter parameters) {
    this.context = context;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return METHOD_NAME;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final String input = parameters.required(request.getParams(), 0, String.class);
    final ProtocolSpec<Void> protocolSpec = context.getProtocolSpec(context.getBlockHeight());
    final ProtocolContext<Void> protocolContext = this.context.getProtocolContext();

    final Block block;
    try {
      block =
          Block.readFrom(
              RLP.input(BytesValue.fromHexString(input)), protocolSpec.getBlockHeaderFunctions());
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.debug("Failed to parse block RLP", e);
      return new JsonRpcSuccessResponse(request.getId(), "0x");
    }

    final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
    if (blockImporter.importBlock(
        protocolContext,
        block,
        context.getHeaderValidationMode(),
        context.getHeaderValidationMode())) {
      return new JsonRpcSuccessResponse(request.getId(), block.getHash().toString());
    } else {
      LOG.debug("Failed to import block.");
      return new JsonRpcSuccessResponse(request.getId(), "0x");
    }
  }
}
