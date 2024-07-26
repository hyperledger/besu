/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes32;

public class PrivateTracer {

  public static <TRACE> Optional<TRACE> processTracing(
      final BlockchainQueries blockchainQueries,
      final Optional<BlockHeader> blockHeader,
      final String privacyGroupId,
      final String enclaveKey,
      final PrivacyParameters privacyParameters,
      final PrivacyController privacyController,
      final Function<PrivateTracer.TraceableState, ? extends Optional<TRACE>> mapper) {

    return blockHeader.flatMap(
        header -> {
          final long blockNumber = header.getNumber();
          final Hash parentHash = header.getParentHash();

          final MutableWorldState disposablePrivateState =
              privacyParameters
                  .getPrivateWorldStateArchive()
                  .getMutable(
                      privacyController
                          .getStateRootByBlockNumber(privacyGroupId, enclaveKey, blockNumber)
                          .get(),
                      parentHash)
                  .get();

          return blockchainQueries.getAndMapWorldState(
              parentHash,
              mutableWorldState ->
                  mapper.apply(
                      new PrivateTracer.TraceableState(mutableWorldState, disposablePrivateState)));
        });
  }

  /**
   * This class force the use of the processTracing method to do tracing. processTracing allows you
   * to cleanly manage the worldstate, to close it etc
   */
  public static class TraceableState implements MutableWorldState {
    private final MutableWorldState mutableWorldState;
    private final MutableWorldState disposableWorldState;

    private TraceableState(
        final MutableWorldState mutableWorldState, final MutableWorldState disposableWorldState) {
      this.mutableWorldState = mutableWorldState;
      this.disposableWorldState = disposableWorldState;
    }

    @Override
    public WorldUpdater updater() {
      return mutableWorldState.updater();
    }

    public WorldUpdater privateUpdater() {
      return disposableWorldState.updater();
    }

    @Override
    public Hash rootHash() {
      return mutableWorldState.rootHash();
    }

    @Override
    public Hash frontierRootHash() {
      return mutableWorldState.rootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(final Bytes32 startKeyHash, final int limit) {
      return mutableWorldState.streamAccounts(startKeyHash, limit);
    }

    @Override
    public Account get(final Address address) {
      return mutableWorldState.get(address);
    }

    @Override
    public void close() throws Exception {
      mutableWorldState.close();
    }

    @Override
    public void persist(final BlockHeader blockHeader) {}
  }
}
