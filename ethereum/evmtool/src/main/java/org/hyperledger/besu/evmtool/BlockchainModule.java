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
 *
 */
package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.worldstate.MutableWorldView;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import org.apache.tuweni.bytes.Bytes32;

@SuppressWarnings("WeakerAccess")
@Module(includes = {GenesisFileModule.class, DataStoreModule.class})
public class BlockchainModule {

  @Singleton
  @Provides
  Blockchain provideBlockchain(
      @Named("GenesisBlock") final Block genesisBlock,
      final BlockchainStorage blockchainStorage,
      final MetricsSystem metricsSystem) {
    return DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem, 0);
  }

  @Provides
  MutableWorldView getMutableWorldView(
      @Named("StateRoot") final Bytes32 stateRoot,
      final WorldStateStorage worldStateStorage,
      final WorldStatePreimageStorage worldStatePreimageStorage,
      final GenesisState genesisState,
      @Named("KeyValueStorageName") final String keyValueStorageName) {
    if ("memory".equals(keyValueStorageName)) {
      final MutableWorldState mutableWorldState =
          new DefaultMutableWorldState(worldStateStorage, worldStatePreimageStorage);
      genesisState.writeStateTo(mutableWorldState);
      return (MutableWorldView) mutableWorldState;
    } else {
      return (MutableWorldView)
          new DefaultMutableWorldState(stateRoot, worldStateStorage, worldStatePreimageStorage);
    }
  }

  @Provides
  WorldStateStorage provideWorldStateStorage(
      @Named("worldState") final KeyValueStorage keyValueStorage) {
    return new WorldStateKeyValueStorage(keyValueStorage);
  }

  @Provides
  WorldStatePreimageStorage provideWorldStatePreimageStorage(
      @Named("worldStatePreimage") final KeyValueStorage keyValueStorage) {
    return new WorldStatePreimageKeyValueStorage(keyValueStorage);
  }

  @Provides
  WorldUpdater provideWorldUpdater(final MutableWorldView mutableWorldView) {
    return mutableWorldView.updater();
  }

  @Provides
  @Named("StateRoot")
  Bytes32 provideStateRoot(final BlockParameter blockParameter, final Blockchain blockchain) {
    if (blockParameter.isEarliest()) {
      return blockchain.getBlockHeader(0).orElseThrow().getStateRoot();
    } else if (blockParameter.isLatest() || blockParameter.isPending()) {
      return blockchain.getChainHeadHeader().getStateRoot();
    } else if (blockParameter.isNumeric()) {
      return blockchain
          .getBlockHeader(blockParameter.getNumber().orElseThrow())
          .orElseThrow()
          .getStateRoot();
    } else {
      return Hash.EMPTY_TRIE_HASH;
    }
  }
}
