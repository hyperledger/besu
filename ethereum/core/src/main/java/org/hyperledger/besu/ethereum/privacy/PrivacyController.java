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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.PrivacyGroup.Type;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.markertransaction.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class PrivacyController {

  private static final Logger LOG = LogManager.getLogger();

  private final Blockchain blockchain;
  private final Enclave enclave;
  private final PrivateTransactionValidator privateTransactionValidator;
  private final PrivateMarkerTransactionFactory privateMarkerTransactionFactory;
  private final PrivateNonceProvider nonceProvider;
  private final PrivateTransactionSimulator privateTransactionSimulator;
  private final PrivateStateStorage privateStateStorage;

  public PrivacyController(
      final Blockchain blockchain,
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory,
      final PrivateNonceProvider nonceProvider,
      final PrivateTransactionSimulator privateTransactionSimulator) {
    this(
        blockchain,
        privacyParameters.getEnclave(),
        new PrivateTransactionValidator(chainId),
        privateMarkerTransactionFactory,
        nonceProvider,
        privateTransactionSimulator,
        privacyParameters.getPrivateStateStorage());
  }

  public PrivacyController(
      final Blockchain blockchain,
      final Enclave enclave,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateMarkerTransactionFactory privateMarkerTransactionFactory,
      final PrivateNonceProvider nonceProvider,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateStateStorage privateStateStorage) {
    this.blockchain = blockchain;
    this.enclave = enclave;
    this.privateTransactionValidator = privateTransactionValidator;
    this.privateMarkerTransactionFactory = privateMarkerTransactionFactory;
    this.nonceProvider = nonceProvider;
    this.privateTransactionSimulator = privateTransactionSimulator;
    this.privateStateStorage = privateStateStorage;
  }

  public SendTransactionResponse sendTransaction(
      final PrivateTransaction privateTransaction, final String enclavePublicKey) {
    try {
      LOG.trace("Storing private transaction in enclave");
      final SendResponse sendResponse = sendRequest(privateTransaction, enclavePublicKey);
      final String enclaveKey = sendResponse.getKey();
      if (privateTransaction.getPrivacyGroupId().isPresent()) {
        final String privacyGroupId = privateTransaction.getPrivacyGroupId().get().toBase64String();
        return new SendTransactionResponse(enclaveKey, privacyGroupId);
      } else {
        final String privateFrom = privateTransaction.getPrivateFrom().toBase64String();
        final String privacyGroupId = getPrivacyGroupId(enclaveKey, privateFrom);
        return new SendTransactionResponse(enclaveKey, privacyGroupId);
      }
    } catch (Exception e) {
      LOG.error("Failed to store private transaction in enclave", e);
      throw e;
    }
  }

  public ReceiveResponse retrieveTransaction(
      final String enclaveKey, final String enclavePublicKey) {
    return enclave.receive(enclaveKey, enclavePublicKey);
  }

  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String enclavePublicKey) {
    return enclave.createPrivacyGroup(addresses, enclavePublicKey, name, description);
  }

  public String deletePrivacyGroup(final String privacyGroupId, final String enclavePublicKey) {
    return enclave.deletePrivacyGroup(privacyGroupId, enclavePublicKey);
  }

  public PrivacyGroup[] findPrivacyGroup(
      final List<String> addresses, final String enclavePublicKey) {
    return enclave.findPrivacyGroup(addresses);
  }

  public List<PrivacyGroup> findOnChainPrivacyGroup(
      final List<String> addresses, final String enclavePublicKey) {
    final ArrayList<PrivacyGroup> privacyGroups = new ArrayList<>();
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage
            .getPrivacyGroupHeadBlockMap(blockchain.getChainHeadHash())
            .orElse(PrivacyGroupHeadBlockMap.EMPTY);
    privacyGroupHeadBlockMap
        .keySet()
        .forEach(
            c -> {
              final List<String> participants = getExistingParticipants(c, enclavePublicKey);
              if (participants.containsAll(addresses)) {
                privacyGroups.add(
                    new PrivacyGroup(
                        c.toBase64String(), PrivacyGroup.Type.PANTHEON, "", "", participants));
              }
            });
    return privacyGroups;
  }

  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {
    return privateMarkerTransactionFactory.create(transactionEnclaveKey, privateTransaction);
  }

  public Transaction createPrivacyMarkerTransaction(
      final String transactionEnclaveKey,
      final PrivateTransaction privateTransaction,
      final Address precompiledAddress) {
    return privateMarkerTransactionFactory.create(
        transactionEnclaveKey, privateTransaction, precompiledAddress);
  }

  public ValidationResult<TransactionValidator.TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction,
      final String privacyGroupId,
      final String enclavePublicKey) {
    return privateTransactionValidator.validate(
        privateTransaction,
        nonceProvider.getNonce(
            privateTransaction.getSender(), Bytes32.wrap(Bytes.fromBase64String(privacyGroupId))));
  }

  public long determineNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String enclavePublicKey) {
    final List<String> groupMembers = Lists.asList(privateFrom, privateFor);

    final List<PrivacyGroup> matchingGroups =
        Lists.newArrayList(enclave.findPrivacyGroup(groupMembers));

    final List<PrivacyGroup> legacyGroups =
        matchingGroups.stream()
            .filter(group -> group.getType() == Type.LEGACY)
            .collect(Collectors.toList());

    if (legacyGroups.size() == 0) {
      // the legacy group does not exist yet
      return 0;
    }
    Preconditions.checkArgument(
        legacyGroups.size() == 1,
        String.format(
            "Found invalid number of privacy groups (%d), expected 1.", legacyGroups.size()));

    final String privacyGroupId = legacyGroups.get(0).getPrivacyGroupId();

    return determineNonce(address, privacyGroupId, enclavePublicKey);
  }

  public long determineNonce(
      final Address sender, final String privacyGroupId, final String enclavePublicKey) {
    return nonceProvider.getNonce(sender, Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)));
  }

  private SendResponse sendRequest(
      final PrivateTransaction privateTransaction, final String enclavePublicKey) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    privateTransaction.writeTo(rlpOutput);
    final String payload = rlpOutput.encoded().toBase64String();

    final List<String> privateFor = resolvePrivateFor(privateTransaction, enclavePublicKey);

    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      PrivacyGroup privacyGroup = null;
      try {
        privacyGroup =
            enclave.retrievePrivacyGroup(
                privateTransaction.getPrivacyGroupId().get().toBase64String());
      } catch (final EnclaveClientException e) {
        // onchain privacy group
      }
      if (privacyGroup != null) {
        return enclave.send(
            payload,
            privateTransaction.getPrivateFrom().toBase64String(),
            privateTransaction.getPrivacyGroupId().get().toBase64String());
      }
    }
    if (privateFor.isEmpty()) {
      privateFor.add(privateTransaction.getPrivateFrom().toBase64String());
    }

    return enclave.send(payload, privateTransaction.getPrivateFrom().toBase64String(), privateFor);
  }

  private List<String> resolvePrivateFor(
      final PrivateTransaction privateTransaction, final String enclavePublicKey) {
    final ArrayList<String> privateFor = new ArrayList<>();
    boolean isLegacyTransaction = privateTransaction.getPrivateFor().isPresent();
    if (isLegacyTransaction) {
      privateFor.addAll(
          privateTransaction.getPrivateFor().get().stream()
              .map(Bytes::toBase64String)
              .collect(Collectors.toList()));
    } else if (isGroupAdditionTransaction(privateTransaction)) {
      privateFor.addAll(getParticipantsFromParameter(privateTransaction.getPayload()));
      privateFor.addAll(
          getExistingParticipants(privateTransaction.getPrivacyGroupId().get(), enclavePublicKey));
    } else {
      privateFor.addAll(
          getExistingParticipants(privateTransaction.getPrivacyGroupId().get(), enclavePublicKey));
    }
    return privateFor;
  }

  private boolean isGroupAdditionTransaction(final PrivateTransaction privateTransaction) {
    return privateTransaction.getTo().isPresent()
        && privateTransaction.getTo().get().equals(Address.PRIVACY_PROXY)
        && privateTransaction.getPayload().toHexString().startsWith("0xf744b089");
  }

  private List<String> getParticipantsFromParameter(final Bytes input) {
    final List<String> participants = new ArrayList<>();
    final Bytes mungedParticipants = input.slice(4 + 32 + 32 + 32);
    for (int i = 0; i <= mungedParticipants.size() - 32; i += 32) {
      participants.add(mungedParticipants.slice(i, 32).toBase64String());
    }
    return participants;
  }

  private List<String> getExistingParticipants(
      final Bytes privacyGroupId, final String enclavePublicKey) {
    // get the privateFor list from the management contract
    final Optional<PrivateTransactionProcessor.Result> privateTransactionSimulatorResultOptional =
        privateTransactionSimulator.process(
            privacyGroupId.toBase64String(),
            enclavePublicKey,
            buildGetParticipantsCallParams(Bytes.fromBase64String(enclavePublicKey)));

    if (privateTransactionSimulatorResultOptional.isPresent()
        && privateTransactionSimulatorResultOptional.get().isSuccessful()) {
      final RLPInput rlpInput =
          RLP.input(privateTransactionSimulatorResultOptional.get().getOutput());
      if (rlpInput.nextSize() > 0) {
        return decodeList(rlpInput.raw());
      } else {
        return Collections.emptyList();
      }

    } else {
      // if the management contract does not exist this will prompt
      // Orion to resolve the privateFor
      return Collections.emptyList();
    }
  }

  private List<String> decodeList(final Bytes rlpEncodedList) {
    final ArrayList<String> decodedElements = new ArrayList<>();
    // first 32 bytes is dynamic list offset
    final UInt256 lengthOfList = UInt256.fromBytes(rlpEncodedList.slice(32, 32)); // length of list
    for (int i = 0; i < lengthOfList.toLong(); ++i) {
      decodedElements.add(
          Bytes.wrap(rlpEncodedList.slice(64 + (32 * i), 32)).toBase64String()); // participant
    }
    return decodedElements;
  }

  private CallParameter buildGetParticipantsCallParams(final Bytes enclavePublicKey) {
    return new CallParameter(
        Address.ZERO,
        Address.PRIVACY_PROXY,
        3000000,
        Wei.of(1000),
        Wei.ZERO,
        Bytes.concatenate(Bytes.fromHexString("0x0b0235be"), enclavePublicKey));
  }

  private String getPrivacyGroupId(final String key, final String privateFrom) {
    LOG.debug("Getting privacy group for {}", privateFrom);
    try {
      return enclave.receive(key, privateFrom).getPrivacyGroupId();
    } catch (final RuntimeException e) {
      LOG.error("Failed to retrieve private transaction in enclave", e);
      throw e;
    }
  }

  public PrivacyGroup retrievePrivacyGroup(final String privacyGroupId) {
    return enclave.retrievePrivacyGroup(privacyGroupId);
  }
}
