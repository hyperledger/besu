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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.retesteth.RetestethContext;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestImportRawBlock implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TestImportRawBlock.class);

  public static final String METHOD_NAME = "test_importRawBlock";

  private final RetestethContext context;

  public TestImportRawBlock(final RetestethContext context) {
    this.context = context;
  }

  @Override
  public String getName() {
    return METHOD_NAME;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final String input = requestContext.getRequiredParameter(0, String.class);
    final ProtocolContext protocolContext = this.context.getProtocolContext();

    final Block block;
    try {
      block =
          Block.readFrom(RLP.input(Bytes.fromHexString(input)), context.getBlockHeaderFunctions());
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.debug("Failed to parse block RLP", e);
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.BLOCK_RLP_IMPORT_ERROR);
    }

    // retesteth expects test_rawImportBlock to not only import the block, but append it to head
    if (context.getBlockchain().contains(block.getHash())) {
      // if we already have the block but it is not head, append it:
      context
          .getBlockchain()
          .appendBlock(
              block,
              context
                  .getBlockchain()
                  .getTxReceipts(block.getHash())
                  .orElse(Collections.emptyList()));
    } else {
      // otherwise attempt to import the block
      final BlockImporter blockImporter =
          context.getProtocolSpec(block.getHeader().getNumber()).getBlockImporter();
      if (!blockImporter.importBlock(
          protocolContext,
          block,
          context.getHeaderValidationMode(),
          context.getHeaderValidationMode())) {
        LOG.debug("Failed to import block.");
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.BLOCK_IMPORT_ERROR);
      }
    }
    // return success on append or import
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), block.getHash().toString());
  }
}
