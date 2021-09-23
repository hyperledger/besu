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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugAccountRangeAtResult;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldState.StreamableAccount;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes32;

public class DebugAccountRange implements JsonRpcMethod {

  private final Supplier<BlockchainQueries> blockchainQueries;

  public DebugAccountRange(final BlockchainQueries blockchainQueries) {
    this(Suppliers.ofInstance(blockchainQueries));
  }

  public DebugAccountRange(final Supplier<BlockchainQueries> blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    // TODO(shemnon) 5229b899 is the last stable commit of retesteth, after this they rename the
    //  method to just "debug_accountRange".  Once the tool is stable we will support the new name.
    return "debug_accountRange";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final BlockParameterOrBlockHash blockParameterOrBlockHash =
        requestContext.getRequiredParameter(0, BlockParameterOrBlockHash.class);
    final String addressHash = requestContext.getRequiredParameter(2, String.class);
    final int maxResults = requestContext.getRequiredParameter(3, Integer.TYPE);

    final Optional<Hash> blockHashOptional = hashFromParameter(blockParameterOrBlockHash);
    if (blockHashOptional.isEmpty()) {
      return emptyResponse(requestContext);
    }
    final Hash blockHash = blockHashOptional.get();
    final Optional<BlockHeader> blockHeaderOptional =
        blockchainQueries.get().blockByHash(blockHash).map(BlockWithMetadata::getHeader);
    if (blockHeaderOptional.isEmpty()) {
      return emptyResponse(requestContext);
    }

    // TODO deal with mid-block locations

    final Optional<WorldState> state =
        blockchainQueries.get().getWorldState(blockHeaderOptional.get().getNumber());

    if (state.isEmpty()) {
      return emptyResponse(requestContext);
    } else {
      final List<StreamableAccount> accounts =
          state
              .get()
              .streamAccounts(Bytes32.fromHexStringLenient(addressHash), maxResults + 1)
              .collect(Collectors.toList());
      Bytes32 nextKey = Bytes32.ZERO;
      if (accounts.size() == maxResults + 1) {
        nextKey = accounts.get(maxResults).getAddressHash();
        accounts.remove(maxResults);
      }

      return new JsonRpcSuccessResponse(
          requestContext.getRequest().getId(),
          new DebugAccountRangeAtResult(
              accounts.stream()
                  .collect(
                      Collectors.toMap(
                          account -> account.getAddressHash().toString(),
                          account -> account.getAddress().orElse(Address.ZERO).toString())),
              nextKey.toString()));
    }
  }

  private Optional<Hash> hashFromParameter(final BlockParameterOrBlockHash blockParameter) {
    if (blockParameter.isEarliest()) {
      return blockchainQueries.get().getBlockHashByNumber(0);
    } else if (blockParameter.isLatest() || blockParameter.isPending()) {
      return blockchainQueries
          .get()
          .latestBlockWithTxHashes()
          .map(block -> block.getHeader().getHash());
    } else if (blockParameter.isNumeric()) {
      return blockchainQueries.get().getBlockHashByNumber(blockParameter.getNumber().getAsLong());
    } else {
      return blockParameter.getHash();
    }
  }

  private JsonRpcSuccessResponse emptyResponse(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new DebugAccountRangeAtResult(Collections.emptyNavigableMap(), null));
  }
}
