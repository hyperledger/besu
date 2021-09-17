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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class GoQuorumKeyValueStoragePrefixedKeyBlockchainStorage
    extends KeyValueStoragePrefixedKeyBlockchainStorage {

  private static final Bytes BLOCK_HEADER_PRIVATE_BLOOM_PREFIX = Bytes.of(8);

  public GoQuorumKeyValueStoragePrefixedKeyBlockchainStorage(
      final KeyValueStorage storage, final BlockHeaderFunctions blockHeaderFunctions) {
    super(storage, blockHeaderFunctions);
  }

  @Override
  public Optional<BlockHeader> getBlockHeader(final Hash blockHash) {
    final Optional<BlockHeader> blockHeaderOptional =
        get(BLOCK_HEADER_PREFIX, blockHash)
            .map(b -> BlockHeader.readFrom(RLP.input(b), blockHeaderFunctions));
    if (blockHeaderOptional.isPresent()) {
      final Optional<Bytes> privateBloomBytesOptional =
          get(BLOCK_HEADER_PRIVATE_BLOOM_PREFIX, blockHash);
      privateBloomBytesOptional.ifPresent(
          pBB -> blockHeaderOptional.get().setPrivateLogsBloom(new LogsBloomFilter(pBB)));
    }
    return blockHeaderOptional;
  }

  @Override
  public Updater updater() {
    return new Updater(storage.startTransaction());
  }

  public static class Updater extends KeyValueStoragePrefixedKeyBlockchainStorage.Updater {

    private Updater(final KeyValueStorageTransaction transaction) {
      super(transaction);
    }

    @Override
    public void putBlockHeader(final Hash blockHash, final BlockHeader blockHeader) {
      set(BLOCK_HEADER_PREFIX, blockHash, RLP.encode(blockHeader::writeTo));
      blockHeader
          .getPrivateLogsBloom()
          .ifPresent(privateLoom -> set(BLOCK_HEADER_PRIVATE_BLOOM_PREFIX, blockHash, privateLoom));
    }
  }
}
