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

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.DefaultEvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.privacy.VersionedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Base64;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class OnChainPrivacyPrecompiledContract extends PrivacyPrecompiledContract {

  // Dummy signature for transactions to not fail being processed.
  private static final SECP256K1.Signature FAKE_SIGNATURE =
      SECP256K1.Signature.create(SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

  private static final Logger LOG = LogManager.getLogger();

  public OnChainPrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    super(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateStorage(),
        privacyParameters.getPrivateStateRootResolver(),
        "OnChainPrivacy");
  }

  @Override
  public Bytes compute(final Bytes input, final MessageFrame messageFrame) {

    if (isMining(messageFrame)) {
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
    final Bytes32 version = versionedPrivateTransaction.getVersion();

    final Optional<Bytes> maybeGroupId = privateTransaction.getPrivacyGroupId();
    if (maybeGroupId.isEmpty()) {
      return Bytes.EMPTY;
    }

    final Bytes32 privacyGroupId = Bytes32.wrap(maybeGroupId.get());

    LOG.debug("Processing private transaction {} in privacy group {}", pmtHash, privacyGroupId);

    final ProcessableBlockHeader currentBlockHeader = messageFrame.getBlockHeader();
    final Hash currentBlockHash = ((BlockHeader) currentBlockHeader).getHash();

    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, currentBlockHash);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

    maybeInjectDefaultManagementAndProxy(
        lastRootHash, disposablePrivateState, privateWorldStateUpdater);

    final Blockchain blockchain = messageFrame.getBlockchain();
    final WorldUpdater publicWorldState = messageFrame.getWorldState();

    if (!canExecute(
        messageFrame,
        currentBlockHeader,
        privateTransaction,
        version,
        publicWorldState,
        privacyGroupId,
        blockchain,
        disposablePrivateState,
        privateWorldStateUpdater)) {
      return Bytes.EMPTY;
    }

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

  boolean canExecute(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final PrivateTransaction privateTransaction,
      final Bytes32 version,
      final WorldUpdater publicWorldState,
      final Bytes32 privacyGroupId,
      final Blockchain blockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {

    final boolean isAddingParticipant =
        privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(OnChainGroupManagement.ADD_TO_GROUP_METHOD_SIGNATURE.toHexString());

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
      return false;
    }
    return true;
  }

  protected boolean isContractLocked(
      final MessageFrame messageFrame,
      final ProcessableBlockHeader currentBlockHeader,
      final WorldUpdater publicWorldState,
      final Bytes32 privacyGroupId,
      final Blockchain blockchain,
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater) {
    final PrivateTransactionProcessor.Result result =
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

  protected PrivateTransactionProcessor.Result simulateTransaction(
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
        privateWorldStateArchive.getMutable(disposablePrivateState.rootHash()).get();
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
      final DefaultEvmAccount managementPrecompile =
          privateWorldStateUpdater.createAccount(Address.DEFAULT_ONCHAIN_PRIVACY_MANAGEMENT);
      final MutableAccount mutableManagementPrecompiled = managementPrecompile.getMutable();
      // this is the code for the simple management contract
      mutableManagementPrecompiled.setCode(
          OnChainGroupManagement.DEFAULT_GROUP_MANAGEMENT_RUNTIME_BYTECODE);

      // inject proxy
      final DefaultEvmAccount proxyPrecompile =
          privateWorldStateUpdater.createAccount(Address.ONCHAIN_PRIVACY_PROXY);
      final MutableAccount mutableProxyPrecompiled = proxyPrecompile.getMutable();
      // this is the code for the proxy contract
      mutableProxyPrecompiled.setCode(OnChainGroupManagement.PROXY_RUNTIME_BYTECODE);
      // manually set the management contract address so the proxy can trust it
      mutableProxyPrecompiled.setStorageValue(
          UInt256.ZERO,
          UInt256.fromBytes(Bytes32.leftPad(Address.DEFAULT_ONCHAIN_PRIVACY_MANAGEMENT)));

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist();
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
    final PrivateTransactionProcessor.Result getVersionResult =
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
