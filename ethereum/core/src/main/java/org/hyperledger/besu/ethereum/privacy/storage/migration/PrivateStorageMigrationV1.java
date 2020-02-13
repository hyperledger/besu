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
package org.hyperledger.besu.ethereum.privacy.storage.migration;

import static org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage.EVENTS_KEY_SUFFIX;
import static org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage.LOGS_KEY_SUFFIX;
import static org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage.METADATA_KEY_SUFFIX;
import static org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage.OUTPUT_KEY_SUFFIX;
import static org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage.REVERT_KEY_SUFFIX;
import static org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage.STATUS_KEY_SUFFIX;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateStorageMigrationTransactionProcessorResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage.Updater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateStorageMigrationV1 implements PrivateStorageMigration {

  private static final Logger LOG = LogManager.getLogger();

  private final PrivateStateStorage privateStateStorage;
  private final Blockchain blockchain;
  private final Enclave enclave;
  private final Bytes enclaveKey;
  private final Address privacyPrecompileAddress;
  private final PrivateStorageMigrationTransactionProcessor transactionProcessor;

  private final List<String> migratedPrivacyGroups = new ArrayList<>();
  private final Map<Hash, List<Hash>> migratedTransactions = new HashMap<>();

  public PrivateStorageMigrationV1(
      final PrivateStateStorage privateStateStorage,
      final Blockchain blockchain,
      final Enclave enclave,
      final Bytes enclaveKey,
      final Address privacyPrecompileAddress,
      final PrivateStorageMigrationTransactionProcessor transactionProcessor) {
    this.privateStateStorage = privateStateStorage;
    this.blockchain = blockchain;
    this.enclave = enclave;
    this.enclaveKey = enclaveKey;
    this.privacyPrecompileAddress = privacyPrecompileAddress;
    this.transactionProcessor = transactionProcessor;
  }

  @Override
  public void migratePrivateStorage() {
    final long migrationStartTimestamp = System.currentTimeMillis();
    final AtomicLong numOfPrivateTxs = new AtomicLong();
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();

    LOG.info("Migrating private storage database...");

    for (int blockNumber = 0; blockNumber <= chainHeadBlockNumber; blockNumber++) {
      final Block block =
          blockchain
              .getBlockByNumber(blockNumber)
              .orElseThrow(PrivateStorageMigrationException::new);
      final Hash blockHash = block.getHash();
      final BlockHeader blockHeader = block.getHeader();
      LOG.info("Processing block {} ({}/{})", blockHash, blockNumber, chainHeadBlockNumber);

      final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
          createPrivacyGroupHeadBlockMap(blockHeader);

      // TODO find out if block has at least one PMT

      // TODO if it does, we should process all transactions in the block

      final List<Transaction> pmtsInBlock = findPMTsInBlock(block);
      for (int pmtIndex = 0; pmtIndex < pmtsInBlock.size(); pmtIndex++) {
        final Transaction pmt = pmtsInBlock.get(pmtIndex);
        LOG.trace("Processing PMT {} ({}/{})", pmt.getHash(), pmtIndex, pmtsInBlock.size() - 1);

        retrievePrivateTransactionFromEnclave(pmt)
            .ifPresent(
                receiveResponse -> {
                  final String privacyGroupId = receiveResponse.getPrivacyGroupId();
                  final PrivateTransaction privateTransaction =
                      parsePrivateTransaction(receiveResponse);
                  final Updater updater = privateStateStorage.updater();

                  final PrivateStorageMigrationTransactionProcessorResult result =
                      migratePrivateTransaction(
                          blockHeader, privacyGroupId, pmt, privateTransaction, updater);

                  if (result.isSuccessful()) {
                    updatePrivacyGroupHeadBlockMap(
                        blockHash, privacyGroupId, privacyGroupHeadBlockMap, updater);
                  }

                  migratedPrivacyGroups.add(privacyGroupId);

                  updater.commit();

                  numOfPrivateTxs.incrementAndGet();
                });
      }
    }

    privateStateStorage.updater().putDatabaseVersion(2).commit();

    deleteLegacyData();

    final long migrationDuration = System.currentTimeMillis() - migrationStartTimestamp;
    LOG.info(
        "Migration took {} seconds to process {} blocks and migrate {} private transactions",
        migrationDuration / 1000.0,
        chainHeadBlockNumber,
        numOfPrivateTxs.get());
  }

  private Optional<ReceiveResponse> retrievePrivateTransactionFromEnclave(final Transaction pmt) {
    final ReceiveResponse receiveResponse;
    try {
      receiveResponse =
          enclave.receive(pmt.getPayload().toBase64String(), enclaveKey.toBase64String());
      return Optional.of(receiveResponse);
    } catch (final EnclaveClientException e) {
      if (e.getStatusCode() == 404) {
        return Optional.empty();
      } else {
        throw new PrivateStorageMigrationException(e);
      }
    } catch (final Exception e) {
      throw new PrivateStorageMigrationException(e);
    }
  }

  private PrivacyGroupHeadBlockMap createPrivacyGroupHeadBlockMap(final BlockHeader blockHeader) {
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockHash =
        new PrivacyGroupHeadBlockMap(
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
                .orElse(PrivacyGroupHeadBlockMap.EMPTY));
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(blockHeader.getHash(), privacyGroupHeadBlockHash)
        .commit();
    return privacyGroupHeadBlockHash;
  }

  private PrivateTransaction parsePrivateTransaction(final ReceiveResponse receiveResponse) {
    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(
            Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())), false);
    final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);

    LOG.trace(
        "Retrieved private transaction {} for privacy group {}",
        privateTransaction.getHash(),
        receiveResponse.getPrivacyGroupId());

    return privateTransaction;
  }

  private PrivateStorageMigrationTransactionProcessorResult migratePrivateTransaction(
      final BlockHeader blockHeader,
      final String privacyGroupId,
      final Transaction privacyMarkerTransaction,
      final PrivateTransaction privateTransaction,
      final Updater updater) {
    final Hash blockHash = blockHeader.getHash();

    final PrivateStorageMigrationTransactionProcessorResult txResult =
        transactionProcessor
            .process(privacyGroupId, privateTransaction, blockHeader)
            .orElseThrow(PrivateStorageMigrationException::new);

    LOG.trace(
        "Executed private transaction {} (result = {}}",
        privateTransaction.getHash(),
        txResult.getResult().getStatus());

    if (txResult.isSuccessful()) {
      createPrivateBlockMetadata(
          blockHeader, privacyGroupId, privacyMarkerTransaction.getHash(), txResult, updater);

      createTransactionReceipt(blockHash, privateTransaction, txResult, updater);

      if (!migratedTransactions.containsKey(blockHash)) {
        migratedTransactions.put(blockHash, new ArrayList<>());
      }
      migratedTransactions.get(blockHash).add(privateTransaction.getHash());
    }

    return txResult;
  }

  private void createPrivateBlockMetadata(
      final BlockHeader blockHeader,
      final String privacyGroupId,
      final Hash privacyMarkerTxHash,
      final PrivateStorageMigrationTransactionProcessorResult txResult,
      final Updater updater) {
    final Bytes32 privacyGroupIdBytes = Bytes32.wrap(Bytes.fromBase64String(privacyGroupId));
    final PrivateBlockMetadata privateBlockMetadata =
        privateStateStorage
            .getPrivateBlockMetadata(blockHeader.getHash(), privacyGroupIdBytes)
            .orElseGet(PrivateBlockMetadata::empty);

    final PrivateTransactionMetadata privateTxMetadata =
        new PrivateTransactionMetadata(
            privacyMarkerTxHash,
            txResult.getResultingRootHash().orElseThrow(PrivateStorageMigrationException::new));
    privateBlockMetadata.addPrivateTransactionMetadata(privateTxMetadata);

    updater.putPrivateBlockMetadata(
        Bytes32.wrap(blockHeader.getHash()), privacyGroupIdBytes, privateBlockMetadata);

    LOG.trace(
        "Created private block metadata for block {}, privacyGroup {}",
        blockHeader.getHash(),
        privacyGroupId);
  }

  private void createTransactionReceipt(
      final Hash blockHash,
      final PrivateTransaction privateTransaction,
      final PrivateStorageMigrationTransactionProcessorResult txResult,
      final Updater updater) {
    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(txResult.getResult());
    updater.putTransactionReceipt(
        blockHash, privateTransaction.getHash(), privateTransactionReceipt);

    LOG.trace(
        "Created transaction receipt for block {}, private transaction {}",
        blockHash,
        privateTransaction.getHash());
  }

  private void updatePrivacyGroupHeadBlockMap(
      final Hash blockHash,
      final String privacyGroupId,
      final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap,
      final Updater updater) {
    final Bytes32 privacyGroupIdBytes = Bytes32.wrap(Bytes.fromBase64String(privacyGroupId));

    if (!privacyGroupHeadBlockMap.contains(privacyGroupIdBytes, blockHash)) {
      privacyGroupHeadBlockMap.put(privacyGroupIdBytes, blockHash);
      updater.putPrivacyGroupHeadBlockMap(blockHash, privacyGroupHeadBlockMap);
    }
  }

  private List<Transaction> findPMTsInBlock(final Block block) {
    return block.getBody().getTransactions().stream()
        .filter(tx -> tx.getTo().isPresent() && tx.getTo().get().equals(privacyPrecompileAddress))
        .collect(Collectors.toList());
  }

  public void deleteLegacyData() {
    final Updater updater = privateStateStorage.updater();
    deleteLegacyStateRootEntries(updater);
    deleteLegacyTransactionDataEntries(updater);
    updater.commit();
  }

  private void deleteLegacyStateRootEntries(final Updater updater) {
    migratedPrivacyGroups.forEach(key -> updater.remove(Bytes.fromBase64String(key), Bytes.EMPTY));
  }

  private void deleteLegacyTransactionDataEntries(final Updater updater) {
    migratedTransactions.forEach(
        (block, txList) -> {
          txList.forEach(
              tx -> {
                deleteLegacyTransactionMetadataEntry(block, tx, updater);
                deleteLegacyTransactionDataEntries(tx, updater);
              });
        });
  }

  private void deleteLegacyTransactionMetadataEntry(
      final Hash blockHash, final Hash transactionHash, final Updater updater) {
    updater.remove(Bytes.concatenate(blockHash, transactionHash), METADATA_KEY_SUFFIX);
  }

  private void deleteLegacyTransactionDataEntries(
      final Hash transactionHash, final Updater updater) {
    updater.remove(transactionHash, EVENTS_KEY_SUFFIX);
    updater.remove(transactionHash, LOGS_KEY_SUFFIX);
    updater.remove(transactionHash, OUTPUT_KEY_SUFFIX);
    updater.remove(transactionHash, STATUS_KEY_SUFFIX);
    updater.remove(transactionHash, REVERT_KEY_SUFFIX);
  }
}
