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

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER;
import static org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils.KEY_TRANSACTION_HASH;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.privacy.FlexiblePrivacyGroupContract;
import org.hyperledger.besu.ethereum.privacy.FlexibleUtil;
import org.hyperledger.besu.ethereum.privacy.PrivateStateGenesisAllocator;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionEvent;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionObserver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.VersionedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.util.Subscribers;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlexiblePrivacyPrecompiledContract extends PrivacyPrecompiledContract {

  private static final Logger LOG =
      LoggerFactory.getLogger(FlexiblePrivacyPrecompiledContract.class);

  private final Subscribers<PrivateTransactionObserver> privateTransactionEventObservers =
      Subscribers.create();

  public FlexiblePrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver,
      final PrivateStateGenesisAllocator privateStateGenesisAllocator,
      final boolean alwaysIncrementPrivateNonce) {
    super(
        gasCalculator,
        enclave,
        worldStateArchive,
        privateStateRootResolver,
        privateStateGenesisAllocator,
        alwaysIncrementPrivateNonce,
        "FlexiblePrivacy");
  }

  public FlexiblePrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    this(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateRootResolver(),
        privacyParameters.getPrivateStateGenesisAllocator(),
        privacyParameters.isPrivateNonceAlwaysIncrementsEnabled());
  }

  public long addPrivateTransactionObserver(final PrivateTransactionObserver observer) {
    return privateTransactionEventObservers.subscribe(observer);
  }

  public boolean removePrivateTransactionObserver(final long observerId) {
    return privateTransactionEventObservers.unsubscribe(observerId);
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {
    if (skipContractExecution(messageFrame)) {
      return NO_RESULT;
    }
    if (input == null || (input.size() != 32 && input.size() != 64)) {
      LOG.error("Can not fetch private transaction payload with key of invalid length {}", input);
      return NO_RESULT;
    }

    final Hash pmtHash = messageFrame.getContextVariable(KEY_TRANSACTION_HASH);

    final String key = input.slice(0, 32).toBase64String();

    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = getReceiveResponse(key);
    } catch (final EnclaveClientException e) {
      LOG.debug("Can not fetch private transaction payload with key {}", key, e);
      return NO_RESULT;
    }

    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(
            Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())), false);
    final VersionedPrivateTransaction versionedPrivateTransaction =
        VersionedPrivateTransaction.readFrom(bytesValueRLPInput);
    final PrivateTransaction privateTransaction =
        versionedPrivateTransaction.getPrivateTransaction();

    final Bytes privateFrom = privateTransaction.getPrivateFrom();
    if (!privateFromMatchesSenderKey(privateFrom, receiveResponse.getSenderKey())) {
      return NO_RESULT;
    }

    final Optional<Bytes> maybeGroupId = privateTransaction.getPrivacyGroupId();
    if (maybeGroupId.isEmpty()) {
      return NO_RESULT;
    }

    final Bytes32 privacyGroupId = Bytes32.wrap(maybeGroupId.get());

    LOG.debug("Processing private transaction {} in privacy group {}", pmtHash, privacyGroupId);

    final ProcessableBlockHeader currentBlockHeader =
        (ProcessableBlockHeader) messageFrame.getBlockValues();

    final PrivateMetadataUpdater privateMetadataUpdater =
        messageFrame.getContextVariable(KEY_PRIVATE_METADATA_UPDATER);
    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, privateMetadataUpdater);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash, null).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

    maybeApplyGenesisToPrivateWorldState(
        lastRootHash,
        disposablePrivateState,
        privateWorldStateUpdater,
        privacyGroupId,
        currentBlockHeader.getNumber());

    if (!canExecute(
        messageFrame,
        currentBlockHeader,
        privateTransaction,
        versionedPrivateTransaction.getVersion(),
        privacyGroupId,
        disposablePrivateState,
        privateWorldStateUpdater,
        privateFrom)) {
      return NO_RESULT;
    }

    final TransactionProcessingResult result =
        processPrivateTransaction(
            messageFrame, privateTransaction, privacyGroupId, privateWorldStateUpdater);

    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process private transaction {}: {}",
          pmtHash,
          result.getValidationResult().getErrorMessage());

      privateMetadataUpdater.putTransactionReceipt(pmtHash, new PrivateTransactionReceipt(result));

      return NO_RESULT;
    }

    sendParticipantRemovedEvent(privateTransaction);

    if (messageFrame.getContextVariable(KEY_IS_PERSISTING_PRIVATE_STATE, false)) {

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist(null);

      storePrivateMetadata(
          pmtHash, privacyGroupId, disposablePrivateState, privateMetadataUpdater, result);
    }

    return new PrecompileContractResult(
        result.getOutput(), true, MessageFrame.State.CODE_EXECUTING, Optional.empty());
  }

  private void sendParticipantRemovedEvent(final PrivateTransaction privateTransaction) {
    if (isRemovingParticipant(privateTransaction)) {
      // get first participant parameter - there is only one for removal transaction
      final String removedParticipant =
          getRemovedParticipantFromParameter(privateTransaction.getPayload());

      final PrivateTransactionEvent removalEvent =
          new PrivateTransactionEvent(
              privateTransaction.getPrivacyGroupId().get().toBase64String(), removedParticipant);
      privateTransactionEventObservers.forEach(
          sub -> sub.onPrivateTransactionProcessed(removalEvent));
    }
  }

  boolean canExecute(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final PrivateTransaction privateTransaction,
      final Bytes32 version,
      final Bytes32 privacyGroupId,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes privateFrom) {
    final FlexiblePrivacyGroupContract flexiblePrivacyGroupContract =
        new FlexiblePrivacyGroupContract(
            messageFrame,
            currentBlockHeader,
            disposablePrivateState,
            privateWorldStateUpdater,
            privateWorldStateArchive,
            privateTransactionProcessor);

    final boolean isAddingParticipant = isAddingParticipant(privateTransaction);
    final boolean isContractLocked = isContractLocked(flexiblePrivacyGroupContract, privacyGroupId);

    if (isAddingParticipant && !isContractLocked) {
      LOG.debug(
          "Privacy Group {} is not locked while trying to add to group with commitment {}",
          privacyGroupId.toHexString(),
          messageFrame.getContextVariable(KEY_TRANSACTION_HASH));
      return false;
    }

    if (isContractLocked && !isTargetingFlexiblePrivacyProxy(privateTransaction)) {
      LOG.debug(
          "Privacy Group {} is locked while trying to execute transaction with commitment {}",
          privacyGroupId.toHexString(),
          messageFrame.getContextVariable(KEY_TRANSACTION_HASH));
      return false;
    }

    if (!flexiblePrivacyGroupVersionMatches(
        flexiblePrivacyGroupContract, privacyGroupId, version)) {
      LOG.debug(
          "Privacy group version mismatch while trying to execute transaction with commitment {}",
          (Hash) messageFrame.getContextVariable(KEY_TRANSACTION_HASH));
      return false;
    }

    if (!isMemberOfPrivacyGroup(
        isAddingParticipant,
        privateTransaction,
        privateFrom,
        flexiblePrivacyGroupContract,
        privacyGroupId)) {
      LOG.debug(
          "PrivateTransaction with hash {} cannot execute in privacy group {} because privateFrom"
              + " {} is not a member.",
          messageFrame.getContextVariable(KEY_TRANSACTION_HASH),
          privacyGroupId.toBase64String(),
          privateFrom.toBase64String());
      return false;
    }

    return true;
  }

  private boolean isMemberOfPrivacyGroup(
      final boolean isAddingParticipant,
      final PrivateTransaction privateTransaction,
      final Bytes privateFrom,
      final FlexiblePrivacyGroupContract flexiblePrivacyGroupContract,
      final Bytes32 privacyGroupId) {
    final List<String> members =
        flexiblePrivacyGroupContract
            .getPrivacyGroupByIdAndBlockHash(privacyGroupId.toBase64String(), Optional.empty())
            .map(PrivacyGroup::getMembers)
            .orElse(Collections.emptyList());

    List<String> participantsFromParameter = Collections.emptyList();
    if (members.isEmpty() && isAddingParticipant) {
      // creating a new group, so we are checking whether the privateFrom is one of the members of
      // the new group
      participantsFromParameter =
          FlexibleUtil.getParticipantsFromParameter(privateTransaction.getPayload());
    }
    final String base64privateFrom = privateFrom.toBase64String();
    return members.contains(base64privateFrom)
        || participantsFromParameter.contains(base64privateFrom);
  }

  private String getRemovedParticipantFromParameter(final Bytes input) {
    return input.slice(4).toBase64String();
  }

  private boolean isTargetingFlexiblePrivacyProxy(final PrivateTransaction privateTransaction) {
    return privateTransaction.getTo().isPresent()
        && privateTransaction.getTo().get().equals(FLEXIBLE_PRIVACY_PROXY);
  }

  private boolean isAddingParticipant(final PrivateTransaction privateTransaction) {
    return isTargetingFlexiblePrivacyProxy(privateTransaction)
        && privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(FlexibleGroupManagement.ADD_PARTICIPANTS_METHOD_SIGNATURE.toHexString());
  }

  private boolean isRemovingParticipant(final PrivateTransaction privateTransaction) {
    return isTargetingFlexiblePrivacyProxy(privateTransaction)
        && privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(FlexibleGroupManagement.REMOVE_PARTICIPANT_METHOD_SIGNATURE.toHexString());
  }

  protected boolean isContractLocked(
      final FlexiblePrivacyGroupContract flexiblePrivacyGroupContract,
      final Bytes32 privacyGroupId) {
    final Optional<Bytes32> canExecuteResult =
        flexiblePrivacyGroupContract.getCanExecute(
            privacyGroupId.toBase64String(), Optional.empty());
    return canExecuteResult.map(Bytes::isZero).orElse(true);
  }

  protected boolean flexiblePrivacyGroupVersionMatches(
      final FlexiblePrivacyGroupContract flexiblePrivacyGroupContract,
      final Bytes32 privacyGroupId,
      final Bytes32 version) {
    final Optional<Bytes32> contractVersionResult =
        flexiblePrivacyGroupContract.getVersion(privacyGroupId.toBase64String(), Optional.empty());
    final boolean versionEqual = contractVersionResult.map(version::equals).orElse(false);

    if (!versionEqual) {
      LOG.debug(
          "Privacy Group {} version mismatch: expecting {} but got {}",
          privacyGroupId.toBase64String(),
          contractVersionResult,
          Optional.of(version));
    }

    return versionEqual;
  }
}
