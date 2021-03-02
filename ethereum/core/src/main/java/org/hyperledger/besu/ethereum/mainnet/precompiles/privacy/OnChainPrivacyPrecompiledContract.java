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

import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionEvent;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionObserver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.privacy.VersionedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.Subscribers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class OnChainPrivacyPrecompiledContract extends PrivacyPrecompiledContract {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  // Dummy signature for transactions to not fail being processed.
  private static final SECPSignature FAKE_SIGNATURE =
      SIGNATURE_ALGORITHM
          .get()
          .createSignature(
              SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
              SIGNATURE_ALGORITHM.get().getHalfCurveOrder(),
              (byte) 0);

  private final Subscribers<PrivateTransactionObserver> privateTransactionEventObservers =
      Subscribers.create();

  private static final Logger LOG = LogManager.getLogger();

  public OnChainPrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateRootResolver privateStateRootResolver) {
    super(gasCalculator, enclave, worldStateArchive, privateStateRootResolver, "OnChainPrivacy");
  }

  public OnChainPrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    super(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateRootResolver(),
        "OnChainPrivacy");
  }

  public long addPrivateTransactionObserver(final PrivateTransactionObserver observer) {
    return privateTransactionEventObservers.subscribe(observer);
  }

  public boolean removePrivateTransactionObserver(final long observerId) {
    return privateTransactionEventObservers.unsubscribe(observerId);
  }

  @Override
  public Bytes compute(final Bytes input, final MessageFrame messageFrame) {

    if (skipContractExecution(messageFrame)) {
      return Bytes.EMPTY;
    }

    final Hash pmtHash = messageFrame.getTransactionHash();

    final String key = input.slice(0, 32).toBase64String();

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
    final VersionedPrivateTransaction versionedPrivateTransaction =
        VersionedPrivateTransaction.readFrom(bytesValueRLPInput);
    final PrivateTransaction privateTransaction =
        versionedPrivateTransaction.getPrivateTransaction();

    final Bytes privateFrom = privateTransaction.getPrivateFrom();
    if (!privateFromMatchesSenderKey(privateFrom, receiveResponse.getSenderKey())) {
      return Bytes.EMPTY;
    }

    final Optional<Bytes> maybeGroupId = privateTransaction.getPrivacyGroupId();
    if (maybeGroupId.isEmpty()) {
      return Bytes.EMPTY;
    }

    final Bytes32 privacyGroupId = Bytes32.wrap(maybeGroupId.get());

    LOG.debug("Processing private transaction {} in privacy group {}", pmtHash, privacyGroupId);

    final ProcessableBlockHeader currentBlockHeader = messageFrame.getBlockHeader();

    final PrivateMetadataUpdater privateMetadataUpdater = messageFrame.getPrivateMetadataUpdater();
    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, privateMetadataUpdater);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash, null).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

    final WorldUpdater publicWorldState = messageFrame.getWorldState();
    final Blockchain blockchain = messageFrame.getBlockchain();

    maybeInjectDefaultManagementAndProxy(
        lastRootHash, disposablePrivateState, privateWorldStateUpdater);

    if (!canExecute(
        messageFrame,
        currentBlockHeader,
        privateTransaction,
        versionedPrivateTransaction.getVersion(),
        publicWorldState,
        privacyGroupId,
        blockchain,
        disposablePrivateState,
        privateWorldStateUpdater,
        privateFrom)) {
      return Bytes.EMPTY;
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

      return Bytes.EMPTY;
    }

    sendParticipantRemovedEvent(privateTransaction);

    if (messageFrame.isPersistingPrivateState()) {

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist(null);

      storePrivateMetadata(
          pmtHash, privacyGroupId, disposablePrivateState, privateMetadataUpdater, result);
    }

    return result.getOutput();
  }

  private void sendParticipantRemovedEvent(final PrivateTransaction privateTransaction) {
    if (privateTransaction.isGroupRemovalTransaction()) {
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
      final WorldUpdater publicWorldState,
      final Bytes32 privacyGroupId,
      final Blockchain blockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes privateFrom) {

    final boolean isAddingParticipant =
        privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(OnChainGroupManagement.ADD_PARTICIPANTS_METHOD_SIGNATURE.toHexString());

    final boolean isPrivacyGroupLocked =
        isContractLocked(
            messageFrame,
            currentBlockHeader,
            publicWorldState,
            privacyGroupId,
            blockchain,
            disposablePrivateState,
            privateWorldStateUpdater);

    if (isAddingParticipant && !isPrivacyGroupLocked) {
      LOG.debug(
          "Privacy Group {} is not locked while trying to add to group with commitment {}",
          privacyGroupId.toHexString(),
          messageFrame.getTransactionHash());
      return false;
    }

    if (!isAddingParticipant && isPrivacyGroupLocked) {
      LOG.debug(
          "Privacy Group {} is locked while trying to execute transaction with commitment {}",
          privacyGroupId.toHexString(),
          messageFrame.getTransactionHash());
      return false;
    }

    if (!onChainPrivacyGroupVersionMatches(
        messageFrame,
        currentBlockHeader,
        version,
        publicWorldState,
        privacyGroupId,
        blockchain,
        disposablePrivateState,
        privateWorldStateUpdater)) {
      LOG.debug(
          "Privacy group version mismatch while trying to execute transaction with commitment {}",
          messageFrame.getTransactionHash());
      return false;
    }

    if (!isMemberOfPrivacyGroup(
        isAddingParticipant,
        privateTransaction,
        privateFrom,
        privacyGroupId,
        messageFrame,
        currentBlockHeader,
        publicWorldState,
        blockchain,
        disposablePrivateState,
        privateWorldStateUpdater)) {
      LOG.debug(
          "PrivateTransaction with hash {} cannot execute in privacy group {} because privateFrom"
              + " {} is not a member.",
          messageFrame.getTransactionHash(),
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
      final Bytes32 privacyGroupId,
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final WorldUpdater publicWorldState,
      final Blockchain blockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {
    final TransactionProcessingResult result =
        simulateTransaction(
            messageFrame,
            currentBlockHeader,
            publicWorldState,
            privacyGroupId,
            blockchain,
            disposablePrivateState,
            privateWorldStateUpdater,
            OnChainGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE);
    final List<Bytes> list = getMembersFromResult(result);
    List<String> participantsFromParameter = Collections.emptyList();
    if (list.isEmpty() && isAddingParticipant) {
      // creating a new group, so we are checking whether the privateFrom is one of the members of
      // the new group
      participantsFromParameter = getParticipantsFromParameter(privateTransaction.getPayload());
    }
    return list.contains(privateFrom)
        || participantsFromParameter.contains(privateFrom.toBase64String());
  }

  List<Bytes> getMembersFromResult(final TransactionProcessingResult result) {
    List<Bytes> list = Collections.emptyList();
    if (result != null && result.isSuccessful()) {
      final RLPInput rlpInput = RLP.input(result.getOutput());
      if (rlpInput.nextSize() > 0) {
        list = decodeList(rlpInput.raw());
      }
    }
    return list;
  }

  // TODO: this method is copied from DefaultPrivacyController. Fix the duplication in a separate GI
  private List<String> getParticipantsFromParameter(final Bytes input) {
    final List<String> participants = new ArrayList<>();
    final Bytes mungedParticipants = input.slice(4 + 32 + 32);
    for (int i = 0; i <= mungedParticipants.size() - 32; i += 32) {
      participants.add(mungedParticipants.slice(i, 32).toBase64String());
    }
    return participants;
  }

  private String getRemovedParticipantFromParameter(final Bytes input) {
    return input.slice(4).toBase64String();
  }

  private List<Bytes> decodeList(final Bytes rlpEncodedList) {
    final ArrayList<Bytes> decodedElements = new ArrayList<>();
    // first 32 bytes is dynamic list offset
    final UInt256 lengthOfList = UInt256.fromBytes(rlpEncodedList.slice(32, 32)); // length of list
    for (int i = 0; i < lengthOfList.toLong(); ++i) {
      decodedElements.add(Bytes.wrap(rlpEncodedList.slice(64 + (32 * i), 32))); // participant
    }
    return decodedElements;
  }

  protected boolean isContractLocked(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final WorldUpdater publicWorldState,
      final Bytes32 privacyGroupId,
      final Blockchain blockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {
    final TransactionProcessingResult result =
        simulateTransaction(
            messageFrame,
            currentBlockHeader,
            publicWorldState,
            privacyGroupId,
            blockchain,
            disposablePrivateState,
            privateWorldStateUpdater,
            OnChainGroupManagement.CAN_EXECUTE_METHOD_SIGNATURE);
    return result.getOutput().toHexString().endsWith("0");
  }

  protected TransactionProcessingResult simulateTransaction(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final WorldUpdater publicWorldState,
      final Bytes32 privacyGroupId,
      final Blockchain currentBlockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes methodSignature) {
    // We need the "lock status" of the group for every single transaction but we don't want this
    // call to affect the state
    // privateTransactionProcessor.processTransaction(...) commits the state if the process was
    // successful before it returns
    final MutableWorldState localMutableState =
        privateWorldStateArchive.getMutable(disposablePrivateState.rootHash(), null).get();
    final WorldUpdater updater = localMutableState.updater();

    return privateTransactionProcessor.processTransaction(
        currentBlockchain,
        publicWorldState,
        updater,
        currentBlockHeader,
        messageFrame.getTransactionHash(),
        buildSimulationTransaction(privacyGroupId, privateWorldStateUpdater, methodSignature),
        messageFrame.getMiningBeneficiary(),
        new DebugOperationTracer(TraceOptions.DEFAULT),
        messageFrame.getBlockHashLookup(),
        privacyGroupId);
  }

  protected void maybeInjectDefaultManagementAndProxy(
      final Hash lastRootHash,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {
    if (lastRootHash.equals(EMPTY_ROOT_HASH)) {
      // inject management
      final EvmAccount managementPrecompile =
          privateWorldStateUpdater.createAccount(Address.DEFAULT_ONCHAIN_PRIVACY_MANAGEMENT);
      final MutableAccount mutableManagementPrecompiled = managementPrecompile.getMutable();
      // this is the code for the simple management contract
      mutableManagementPrecompiled.setCode(
          OnChainGroupManagement.DEFAULT_GROUP_MANAGEMENT_RUNTIME_BYTECODE);

      // inject proxy
      final EvmAccount proxyPrecompile =
          privateWorldStateUpdater.createAccount(Address.ONCHAIN_PRIVACY_PROXY);
      final MutableAccount mutableProxyPrecompiled = proxyPrecompile.getMutable();
      // this is the code for the proxy contract
      mutableProxyPrecompiled.setCode(OnChainGroupManagement.PROXY_RUNTIME_BYTECODE);
      // manually set the management contract address so the proxy can trust it
      mutableProxyPrecompiled.setStorageValue(
          UInt256.ZERO,
          UInt256.fromBytes(Bytes32.leftPad(Address.DEFAULT_ONCHAIN_PRIVACY_MANAGEMENT)));

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist(null);
    }
  }

  protected boolean onChainPrivacyGroupVersionMatches(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final Bytes32 version,
      final WorldUpdater publicWorldState,
      final Bytes32 privacyGroupId,
      final Blockchain currentBlockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {
    // We need the "version" of the group for every single transaction but we don't want this
    // call to affect the state
    // privateTransactionProcessor.processTransaction(...) commits the state if the process was
    // successful before it returns
    final TransactionProcessingResult getVersionResult =
        simulateTransaction(
            messageFrame,
            currentBlockHeader,
            publicWorldState,
            privacyGroupId,
            currentBlockchain,
            disposablePrivateState,
            privateWorldStateUpdater,
            OnChainGroupManagement.GET_VERSION_METHOD_SIGNATURE);

    if (version.equals(getVersionResult.getOutput())) {
      return true;
    }
    LOG.debug(
        "Privacy Group {} version mismatch for commitment {}: expecting {} but got {}",
        privacyGroupId.toBase64String(),
        messageFrame.getTransactionHash(),
        getVersionResult.getOutput(),
        version);
    return false;
  }

  private PrivateTransaction buildSimulationTransaction(
      final Bytes privacyGroupId,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes payload) {
    return PrivateTransaction.builder()
        .privateFrom(Bytes.EMPTY)
        .privacyGroupId(privacyGroupId)
        .restriction(Restriction.RESTRICTED)
        .nonce(
            privateWorldStateUpdater.getAccount(Address.ZERO) != null
                ? privateWorldStateUpdater.getAccount(Address.ZERO).getNonce()
                : 0)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(Address.ONCHAIN_PRIVACY_PROXY)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .payload(payload)
        .signature(FAKE_SIGNATURE)
        .build();
  }
}
