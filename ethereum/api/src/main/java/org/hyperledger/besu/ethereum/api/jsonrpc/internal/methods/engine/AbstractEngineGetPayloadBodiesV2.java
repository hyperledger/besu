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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Optional;

import io.vertx.core.Vertx;

public abstract class AbstractEngineGetPayloadBodiesV2 extends ExecutionEngineJsonRpcMethod {
  protected static final int MAX_REQUEST_BLOCKS = 1024;

  protected AbstractEngineGetPayloadBodiesV2(
      Vertx vertx, ProtocolContext protocolContext, EngineCallListener engineCallListener) {
    super(vertx, protocolContext, engineCallListener);
  }

  protected int getMaxRequestBlocks() {
    return MAX_REQUEST_BLOCKS;
  }

  protected Optional<String> getBlockAccessList(final Blockchain blockchain, final Hash blockHash) {
    return blockchain
        .getBlockAccessList(blockHash)
        .map(AbstractEngineGetPayloadBodiesV2::encodeBlockAccessList);
  }

  protected static String encodeBlockAccessList(final BlockAccessList blockAccessList) {
    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    blockAccessList.writeTo(output);
    return output.encoded().toHexString();
  }
}
