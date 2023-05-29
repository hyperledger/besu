/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.CHAIN_HEAD_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FINALIZED_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FORK_HEADS;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SAFE_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SEQ_NO_STORE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.VariablesStorage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Collection;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VariablesKeyValueStorage implements VariablesStorage {
  final KeyValueStorage variables;

  public VariablesKeyValueStorage(final KeyValueStorage variables) {
    this.variables = variables;
  }

  @Override
  public Optional<Hash> getChainHead() {
    return getVariable(CHAIN_HEAD_HASH).map(this::bytesToHash);
  }

  @Override
  public Collection<Hash> getForkHeads() {
    return getVariable(FORK_HEADS)
        .map(bytes -> RLP.input(bytes).readList(in -> this.bytesToHash(in.readBytes32())))
        .orElse(Lists.newArrayList());
  }

  @Override
  public Optional<Hash> getFinalized() {
    return getVariable(FINALIZED_BLOCK_HASH).map(this::bytesToHash);
  }

  @Override
  public Optional<Hash> getSafeBlock() {
    return getVariable(SAFE_BLOCK_HASH).map(this::bytesToHash);
  }

  @Override
  public Optional<Bytes> getLocalEnrSeqno() {
    return getVariable(SEQ_NO_STORE).map(Bytes::wrap);
  }

  @Override
  public Updater updater() {
    return new Updater(variables.startTransaction());
  }

  private Hash bytesToHash(final Bytes bytes) {
    return Hash.wrap(Bytes32.wrap(bytes, 0));
  }

  Optional<Bytes> getVariable(final Keys key) {
    return variables.get(key.toByteArray()).map(Bytes::wrap);
  }

  @Override
  public void migrate(
      final ProtocolSchedule protocolSchedule, final StorageProvider storageProvider) {
    final var blockchainStorage = storageProvider.createBlockchainStorage(protocolSchedule, this);
    final var blockchainUpdater = blockchainStorage.updater();
    final var variablesUpdater = updater();

    blockchainStorage.getChainHead().ifPresent(variablesUpdater::setChainHead);

    blockchainStorage.getFinalized().ifPresent(variablesUpdater::setFinalized);

    blockchainStorage.getSafeBlock().ifPresent(variablesUpdater::setSafeBlock);

    variablesUpdater.setForkHeads(blockchainStorage.getForkHeads());

    blockchainUpdater.removeVariables();

    variablesUpdater.commit();
    blockchainUpdater.commit();

    migrateSeqNoStore(storageProvider);
  }

  private void migrateSeqNoStore(final StorageProvider storageProvider) {
    final var blockchainStorage =
        storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);

    blockchainStorage
        .get(SEQ_NO_STORE.toByteArray())
        .map(Bytes::wrap)
        .ifPresent(
            nodeRecord -> {
              final var variablesUpdater = updater();
              final var blockchainUpdater = blockchainStorage.startTransaction();
              variablesUpdater.setLocalEnrSeqno(nodeRecord);
              blockchainUpdater.remove(SEQ_NO_STORE.toByteArray());
              variablesUpdater.commit();
              blockchainUpdater.commit();
            });
  }

  @Override
  public void revert(
      final ProtocolSchedule protocolSchedule, final StorageProvider storageProvider) {
    final var blockchainStorage = storageProvider.createBlockchainStorage(protocolSchedule, this);
    final var blockchainUpdater = blockchainStorage.updater();
    final var variablesUpdater = updater();

    getChainHead().ifPresent(blockchainUpdater::setChainHead);

    getFinalized().ifPresent(blockchainUpdater::setFinalized);

    getSafeBlock().ifPresent(blockchainUpdater::setSafeBlock);

    blockchainUpdater.setForkHeads(getForkHeads());

    revertSeqNoStore(storageProvider);

    variablesUpdater.removeAll();

    variablesUpdater.commit();
    blockchainUpdater.commit();
  }

  private void revertSeqNoStore(final StorageProvider storageProvider) {
    final var blockchainStorage =
        storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);

    getLocalEnrSeqno()
        .map(Bytes::toArray)
        .ifPresent(
            nodeRecord -> {
              final var blockchainUpdater = blockchainStorage.startTransaction();
              blockchainUpdater.put(SEQ_NO_STORE.toByteArray(), nodeRecord);
              blockchainUpdater.commit();
            });
  }

  public static class Updater implements VariablesStorage.Updater {

    private final KeyValueStorageTransaction variablesTransaction;

    Updater(final KeyValueStorageTransaction variablesTransaction) {
      this.variablesTransaction = variablesTransaction;
    }

    @Override
    public void setChainHead(final Hash blockHash) {
      setVariable(CHAIN_HEAD_HASH, blockHash);
    }

    @Override
    public void setForkHeads(final Collection<Hash> forkHeadHashes) {
      final Bytes data =
          RLP.encode(o -> o.writeList(forkHeadHashes, (val, out) -> out.writeBytes(val)));
      setVariable(FORK_HEADS, data);
    }

    @Override
    public void setFinalized(final Hash blockHash) {
      setVariable(FINALIZED_BLOCK_HASH, blockHash);
    }

    @Override
    public void setSafeBlock(final Hash blockHash) {
      setVariable(SAFE_BLOCK_HASH, blockHash);
    }

    @Override
    public void setLocalEnrSeqno(final Bytes nodeRecord) {
      setVariable(SEQ_NO_STORE, nodeRecord);
    }

    @Override
    public void removeAll() {
      removeVariable(CHAIN_HEAD_HASH);
      removeVariable(FINALIZED_BLOCK_HASH);
      removeVariable(SAFE_BLOCK_HASH);
      removeVariable(FORK_HEADS);
      removeVariable(SEQ_NO_STORE);
    }

    @Override
    public void commit() {
      variablesTransaction.commit();
    }

    @Override
    public void rollback() {
      variablesTransaction.rollback();
    }

    void setVariable(final Keys key, final Bytes value) {
      variablesTransaction.put(key.toByteArray(), value.toArrayUnsafe());
    }

    void removeVariable(final Keys key) {
      variablesTransaction.remove(key.toByteArray());
    }
  }
}
