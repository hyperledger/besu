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
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
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
  final WorldStateArchive privateWorldStateArchive;
  final PrivateStateRootResolver privateStateRootResolver;
  PrivateTransactionProcessor privateTransactionProcessor;

  private static final Logger LOG = LogManager.getLogger();

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final PrivacyParameters privacyParameters,
      final String name) {
    this(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateRootResolver(),
        name);
  }

  @VisibleForTesting
  PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver) {
    this(gasCalculator, enclave, worldStateArchive, privateStateRootResolver, "Privacy");
  }

  protected PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final String name) {
    super(name, gasCalculator);
    this.enclave = enclave;
    this.privateWorldStateArchive = worldStateArchive;
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

    final Bytes privateFrom = privateTransaction.getPrivateFrom();
    if (!privateFromMatchesSenderKey(privateFrom, receiveResponse.getSenderKey())) {
      return Bytes.EMPTY;
    }

    final Bytes32 privacyGroupId =
        Bytes32.wrap(Bytes.fromBase64String(receiveResponse.getPrivacyGroupId()));

    try {
      if (privateTransaction.getPrivateFor().isEmpty()
          && !enclave
              .retrievePrivacyGroup(privacyGroupId.toBase64String())
              .getMembers()
              .contains(privateFrom.toBase64String())) {
        return Bytes.EMPTY;
      }
    } catch (final EnclaveClientException e) {
      // This exception is thrown when the privacy group can not be found
      return Bytes.EMPTY;
    } catch (final EnclaveServerException e) {
      LOG.error("Enclave is responding with an error, perhaps it has a misconfiguration?", e);
      throw e;
    } catch (final EnclaveIOException e) {
      LOG.error("Can not communicate with enclave, is it up?", e);
      throw e;
    }

    LOG.debug("Processing private transaction {} in privacy group {}", pmtHash, privacyGroupId);

    final PrivateMetadataUpdater privateMetadataUpdater = messageFrame.getPrivateMetadataUpdater();
    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, privateMetadataUpdater);

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

      privateMetadataUpdater.putTransactionReceipt(pmtHash, new PrivateTransactionReceipt(result));

      return Bytes.EMPTY;
    }

    if (messageFrame.isPersistingPrivateState()) {

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist();

      storePrivateMetadata(
          pmtHash, privacyGroupId, disposablePrivateState, privateMetadataUpdater, result);
    }

    return result.getOutput();
  }

  void storePrivateMetadata(
      final Hash commitmentHash,
      final Bytes32 privacyGroupId,
      final MutableWorldState disposablePrivateState,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final PrivateTransactionProcessor.Result result) {

    final int txStatus =
        result.getStatus() == PrivateTransactionProcessor.Result.Status.SUCCESSFUL ? 1 : 0;

    final PrivateTransactionReceipt privateTransactionReceipt =
        new PrivateTransactionReceipt(
            txStatus, result.getLogs(), result.getOutput(), result.getRevertReason());

    privateMetadataUpdater.putTransactionReceipt(commitmentHash, privateTransactionReceipt);
    privateMetadataUpdater.updatePrivacyGroupHeadBlockMap(privacyGroupId);
    privateMetadataUpdater.addPrivateTransactionMetadata(
        privacyGroupId,
        new PrivateTransactionMetadata(commitmentHash, disposablePrivateState.rootHash()));
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
}
