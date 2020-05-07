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

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveIOException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Base64;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivacyPrecompiledContract extends AbstractPrecompiledContract {
  final Enclave enclave;
  final PrivateStateStorage privateStateStorage;
  final WorldStateArchive privateWorldStateArchive;
  final PrivateStateRootResolver privateStateRootResolver;
  PrivateTransactionProcessor privateTransactionProcessor;

  private static final Logger LOG = LogManager.getLogger();

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    this(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateStorage(),
        privacyParameters.getPrivateStateRootResolver(),
        "Privacy");
  }

  @VisibleForTesting
  PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateStorage privateStateStorage,
      final PrivateStateRootResolver privateStateRootResolver) {
    this(
        gasCalculator,
        enclave,
        worldStateArchive,
        privateStateStorage,
        privateStateRootResolver,
        "Privacy");
  }

  protected PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateStorage privateStateStorage,
      final PrivateStateRootResolver privateStateRootResolver,
      final String name) {
    super(name, gasCalculator);
    this.enclave = enclave;
    this.privateWorldStateArchive = worldStateArchive;
    this.privateStateStorage = privateStateStorage;
    this.privateStateRootResolver = privateStateRootResolver;
  }

  public void setPrivateTransactionProcessor(
      final PrivateTransactionProcessor privateTransactionProcessor) {
    this.privateTransactionProcessor = privateTransactionProcessor;
  }

  @Override
  public Gas gasRequirement(final Bytes input) {
    return Gas.of(0L);
  }

  @Override
  public Bytes compute(final Bytes input, final MessageFrame messageFrame) {

    if (isMining(messageFrame)) {
      return Bytes.EMPTY;
    }

    final Hash pmtHash = messageFrame.getTransactionHash();

    final String key = input.toBase64String();
    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = getReceiveResponse(key);
    } catch (final EnclaveClientException e) {
      LOG.debug("Can not fetch private transaction payload with key {}", key, e);
      return Bytes.EMPTY;
    }

    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(
            Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())), false);
    final PrivateTransaction privateTransaction =
        PrivateTransaction.readFrom(bytesValueRLPInput.readAsRlp());

    final Bytes32 privacyGroupId =
        Bytes32.wrap(Bytes.fromBase64String(receiveResponse.getPrivacyGroupId()));

    LOG.debug("Processing private transaction {} in privacy group {}", pmtHash, privacyGroupId);

    final Hash currentBlockHash = ((BlockHeader) messageFrame.getBlockHeader()).getHash();

    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, currentBlockHash);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

    final PrivateTransactionProcessor.Result result =
        processPrivateTransaction(
            messageFrame, privateTransaction, privacyGroupId, privateWorldStateUpdater);

    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process private transaction {}: {}",
          pmtHash,
          result.getValidationResult().getErrorMessage());
      return Bytes.EMPTY;
    }

    if (messageFrame.isPersistingPrivateState()) {
      persistPrivateState(
          pmtHash,
          currentBlockHash,
          privateTransaction,
          privacyGroupId,
          disposablePrivateState,
          privateWorldStateUpdater,
          result);
    }

    return result.getOutput();
  }

  void persistPrivateState(
      final Hash commitmentHash,
      final Hash currentBlockHash,
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final PrivateTransactionProcessor.Result result) {

    LOG.trace(
        "Persisting private state {} for privacyGroup {}",
        disposablePrivateState.rootHash(),
        privacyGroupId);

    privateWorldStateUpdater.commit();
    disposablePrivateState.persist();

    final PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();

    updatePrivateBlockMetadata(
        commitmentHash,
        currentBlockHash,
        privacyGroupId,
        disposablePrivateState.rootHash(),
        privateStateUpdater);

    final int txStatus =
        result.getStatus() == PrivateTransactionProcessor.Result.Status.SUCCESSFUL ? 1 : 0;

    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(
            txStatus, result.getLogs(), result.getOutput(), result.getRevertReason());

    privateStateUpdater.putTransactionReceipt(
        currentBlockHash, commitmentHash, privateTransactionReceipt);

    maybeUpdateGroupHeadBlockMap(privacyGroupId, currentBlockHash, privateStateUpdater);

    privateStateUpdater.commit();
  }

  void maybeUpdateGroupHeadBlockMap(
      final Bytes32 privacyGroupId,
      final Hash currentBlockHash,
      final PrivateStateStorage.Updater privateStateUpdater) {

    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage.getPrivacyGroupHeadBlockMap(currentBlockHash).orElseThrow();

    if (!privacyGroupHeadBlockMap.contains(Bytes32.wrap(privacyGroupId), currentBlockHash)) {
      privacyGroupHeadBlockMap.put(Bytes32.wrap(privacyGroupId), currentBlockHash);
      privateStateUpdater.putPrivacyGroupHeadBlockMap(
          currentBlockHash, new PrivacyGroupHeadBlockMap(privacyGroupHeadBlockMap));
    }
  }

  PrivateTransactionProcessor.Result processPrivateTransaction(
      final MessageFrame messageFrame,
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final WorldUpdater privateWorldStateUpdater) {

    return privateTransactionProcessor.processTransaction(
        messageFrame.getBlockchain(),
        messageFrame.getWorldState(),
        privateWorldStateUpdater,
        messageFrame.getBlockHeader(),
        messageFrame.getTransactionHash(),
        privateTransaction,
        messageFrame.getMiningBeneficiary(),
        new DebugOperationTracer(TraceOptions.DEFAULT),
        messageFrame.getBlockHashLookup(),
        privacyGroupId);
  }

  ReceiveResponse getReceiveResponse(final String key) {
    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = enclave.receive(key);
    } catch (final EnclaveServerException e) {
      LOG.error("Enclave is responding with an error, perhaps it has a misconfiguration?", e);
      throw e;
    } catch (final EnclaveIOException e) {
      LOG.error("Can not communicate with enclave is it up?", e);
      throw e;
    }
    return receiveResponse;
  }

  boolean isMining(final MessageFrame messageFrame) {
    boolean isMining = false;
    final ProcessableBlockHeader currentBlockHeader = messageFrame.getBlockHeader();
    if (!BlockHeader.class.isAssignableFrom(currentBlockHeader.getClass())) {
      if (!messageFrame.isPersistingPrivateState()) {
        isMining = true;
      } else {
        throw new IllegalArgumentException(
            "The MessageFrame contains an illegal block header type. Cannot persist private block metadata without current block hash.");
      }
    }
    return isMining;
  }

  void updatePrivateBlockMetadata(
      final Hash markerTransactionHash,
      final Hash currentBlockHash,
      final Bytes32 privacyGroupId,
      final Hash rootHash,
      final PrivateStateStorage.Updater privateStateUpdater) {
    final PrivateBlockMetadata privateBlockMetadata =
        privateStateStorage
            .getPrivateBlockMetadata(currentBlockHash, Bytes32.wrap(privacyGroupId))
            .orElseGet(PrivateBlockMetadata::empty);
    privateBlockMetadata.addPrivateTransactionMetadata(
        new PrivateTransactionMetadata(markerTransactionHash, rootHash));
    privateStateUpdater.putPrivateBlockMetadata(
        Bytes32.wrap(currentBlockHash), Bytes32.wrap(privacyGroupId), privateBlockMetadata);
  }
}
