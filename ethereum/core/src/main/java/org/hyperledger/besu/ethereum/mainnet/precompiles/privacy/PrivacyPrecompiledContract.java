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
package org.hyperledger.besu.ethereum.mainnet.precompiles.privacy;

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateGroupIdToLatestBlockWithTransactionMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Collections;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyPrecompiledContract extends AbstractPrecompiledContract {
  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH);

  private final Enclave enclave;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateStorage privateStateStorage;
  private final PrivateStateRootResolver privateStateRootResolver;
  private PrivateTransactionProcessor privateTransactionProcessor;

  private static final Logger LOG = LogManager.getLogger();

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    this(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateStorage());
  }

  PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateStorage privateStateStorage) {
    super("Privacy", gasCalculator);
    this.enclave = enclave;
    this.privateWorldStateArchive = worldStateArchive;
    this.privateStateStorage = privateStateStorage;
    this.privateStateRootResolver = new PrivateStateRootResolver(privateStateStorage);
  }

  public void setPrivateTransactionProcessor(
      final PrivateTransactionProcessor privateTransactionProcessor) {
    this.privateTransactionProcessor = privateTransactionProcessor;
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return Gas.of(0L);
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final String key = BytesValues.asBase64String(input);
    final ReceiveRequest receiveRequest = new ReceiveRequest(key);

    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = enclave.receive(receiveRequest);
    } catch (final Exception e) {
      LOG.debug("Enclave does not have private transaction payload with key {}", key, e);
      return BytesValue.EMPTY;
    }

    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(BytesValues.fromBase64(receiveResponse.getPayload()), false);
    final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);
    final WorldUpdater publicWorldState = messageFrame.getWorldState();
    final BytesValue privacyGroupId = BytesValues.fromBase64(receiveResponse.getPrivacyGroupId());

  LOG.trace(
          "Processing private transaction {} in privacy group {}",
          privateTransaction.getHash(),
          privacyGroupId);

    final Map<Bytes32, Hash> privacyGroupToLatestBlockWithTransactionMap =
        privateStateStorage
            .getPrivacyGroupToLatestBlockWithTransactionMap(
                messageFrame.getBlockHeader().getParentHash())
            .map(PrivateGroupIdToLatestBlockWithTransactionMap::getMap)
            .orElse(Collections.emptyMap());

    final Hash lastRootHash;
    if (privacyGroupToLatestBlockWithTransactionMap.containsKey(Bytes32.wrap(privacyGroupId))) {
      // Check this PG head block is being tracked
      final Hash blockHeaderHash =
          privacyGroupToLatestBlockWithTransactionMap.get(Bytes32.wrap(privacyGroupId));
      lastRootHash =
          privateStateRootResolver.resolveLastStateRoot(
              messageFrame.getBlockchain(),
              messageFrame.getBlockchain().getBlockHeader(blockHeaderHash).get(),
              privacyGroupId);
    } else if (BlockHeader.class.isAssignableFrom(messageFrame.getBlockHeader().getClass())
        && privateStateStorage
            .getPrivateBlockMetadata(
                ((BlockHeader) messageFrame.getBlockHeader()).getHash(),
                Bytes32.wrap(privacyGroupId))
            .isPresent()) {
      // Check if PG is not tracked and this is not the first transaction for this PG
      lastRootHash =
          privateStateStorage
              .getPrivateBlockMetadata(
                  ((BlockHeader) messageFrame.getBlockHeader()).getHash(),
                  Bytes32.wrap(privacyGroupId))
              .get()
              .getLatestStateRoot();
    } else {
      // First transaction for this PG
      lastRootHash = EMPTY_ROOT_HASH;
    }

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();
    final PrivateTransactionProcessor.Result result =
        privateTransactionProcessor.processTransaction(
            messageFrame.getBlockchain(),
            publicWorldState,
            privateWorldStateUpdater,
            messageFrame.getBlockHeader(),
            privateTransaction,
            messageFrame.getMiningBeneficiary(),
            new DebugOperationTracer(TraceOptions.DEFAULT),
            messageFrame.getBlockHashLookup(),
            privacyGroupId);

    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process private transaction {}: {}",
          privateTransaction.getHash(),
          result.getValidationResult().getErrorMessage());
      return BytesValue.EMPTY;
    }

    if (messageFrame.isPersistingState()) {
      LOG.trace(
          "Persisting private state {} for privacyGroup {}",
          disposablePrivateState.rootHash(),
          privacyGroupId);
      privateWorldStateUpdater.commit();
      disposablePrivateState.persist();

      final PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();
      final BlockHeader headerWithHash = (BlockHeader) messageFrame.getBlockHeader();
      final PrivateBlockMetadata privateBlockMetadata =
          privateStateStorage
              .getPrivateBlockMetadata(headerWithHash.getHash(), Bytes32.wrap(privacyGroupId))
              .orElseGet(PrivateBlockMetadata::empty);
      privateBlockMetadata.addPrivateTransactionMetadata(
          new PrivateTransactionMetadata(
              messageFrame.getTransactionHash(), disposablePrivateState.rootHash()));
      privateStateUpdater.putPrivateBlockMetadata(
          Bytes32.wrap(headerWithHash.getBlockHash().getByteArray()),
          Bytes32.wrap(privacyGroupId),
          privateBlockMetadata);

      final Bytes32 txHash = keccak256(RLP.encode(privateTransaction::writeTo));
      final LogSeries logs = result.getLogs();
      if (!logs.isEmpty()) {
        privateStateUpdater.putTransactionLogs(txHash, result.getLogs());
      }
      if (result.getRevertReason().isPresent()) {
        privateStateUpdater.putTransactionRevertReason(txHash, result.getRevertReason().get());
      }

      privateStateUpdater.putTransactionStatus(
          txHash,
          BytesValue.of(
              result.getStatus() == PrivateTransactionProcessor.Result.Status.SUCCESSFUL ? 1 : 0));
      privateStateUpdater.putTransactionResult(txHash, result.getOutput());
      privateStateUpdater.commit();
    }

    return result.getOutput();
  }
}
