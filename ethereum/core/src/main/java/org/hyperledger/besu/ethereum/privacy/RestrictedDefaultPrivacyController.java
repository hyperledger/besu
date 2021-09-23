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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.ONCHAIN_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement.ADD_PARTICIPANTS_METHOD_SIGNATURE;
import static org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE;
import static org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement.GET_VERSION_METHOD_SIGNATURE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
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

public class RestrictedDefaultPrivacyController implements PrivacyController {

  private static final Logger LOG = LogManager.getLogger();

  private final Blockchain blockchain;
  private final PrivateStateStorage privateStateStorage;
  private final Enclave enclave;
  private final PrivateTransactionValidator privateTransactionValidator;
  private final PrivateTransactionSimulator privateTransactionSimulator;
  private final PrivateNonceProvider privateNonceProvider;
  private final PrivateWorldStateReader privateWorldStateReader;
  private final PrivateTransactionLocator privateTransactionLocator;
  private final PrivateStateRootResolver privateStateRootResolver;

  public RestrictedDefaultPrivacyController(
      final Blockchain blockchain,
      final PrivacyParameters privacyParameters,
      final Optional<BigInteger> chainId,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader) {
    this(
        blockchain,
        privacyParameters.getPrivateStateStorage(),
        privacyParameters.getEnclave(),
        new PrivateTransactionValidator(chainId),
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader,
        privacyParameters.getPrivateStateRootResolver());
  }

  public RestrictedDefaultPrivacyController(
      final Blockchain blockchain,
      final PrivateStateStorage privateStateStorage,
      final Enclave enclave,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader,
      final PrivateStateRootResolver privateStateRootResolver) {
    this.blockchain = blockchain;
    this.privateStateStorage = privateStateStorage;
    this.enclave = enclave;
    this.privateTransactionValidator = privateTransactionValidator;
    this.privateTransactionSimulator = privateTransactionSimulator;
    this.privateNonceProvider = privateNonceProvider;
    this.privateWorldStateReader = privateWorldStateReader;
    this.privateTransactionLocator =
        new PrivateTransactionLocator(blockchain, enclave, privateStateStorage);
    this.privateStateRootResolver = privateStateRootResolver;
  }

  @Override
  public Optional<ExecutedPrivateTransaction> findPrivateTransactionByPmtHash(
      final Hash pmtHash, final String enclaveKey) {
    return privateTransactionLocator.findByPmtHash(pmtHash, enclaveKey);
  }

  @Override
  public String createPrivateMarkerTransactionPayload(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> maybePrivacyGroup) {
    try {
      LOG.trace("Storing private transaction in enclave");
      final SendResponse sendResponse =
          sendRequest(privateTransaction, privacyUserId, maybePrivacyGroup);
      return sendResponse.getKey();
    } catch (final Exception e) {
      LOG.error("Failed to store private transaction in enclave", e);
      throw e;
    }
  }

  @Override
  public ReceiveResponse retrieveTransaction(final String enclaveKey, final String privacyUserId) {
    return enclave.receive(enclaveKey, privacyUserId);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String privacyUserId) {
    return enclave.createPrivacyGroup(addresses, privacyUserId, name, description);
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String privacyUserId) {
    return enclave.deletePrivacyGroup(privacyGroupId, privacyUserId);
  }

  @Override
  public PrivacyGroup[] findOffChainPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    return enclave.findPrivacyGroup(addresses);
  }

  @Override
  public ValidationResult<TransactionInvalidReason> validatePrivateTransaction(
      final PrivateTransaction privateTransaction, final String privacyUserId) {
    final String privacyGroupId = privateTransaction.determinePrivacyGroupId().toBase64String();
    return privateTransactionValidator.validate(
        privateTransaction,
        determineBesuNonce(privateTransaction.getSender(), privacyGroupId, privacyUserId),
        true);
  }

  @Override
  public long determineEeaNonce(
      final String privateFrom,
      final String[] privateFor,
      final Address address,
      final String privacyUserId) {
    final List<String> groupMembers = Lists.asList(privateFrom, privateFor);

    final List<PrivacyGroup> matchingGroups =
        Lists.newArrayList(enclave.findPrivacyGroup(groupMembers));

    final List<PrivacyGroup> legacyGroups =
        matchingGroups.stream()
            .filter(group -> group.getType() == PrivacyGroup.Type.LEGACY)
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

    return determineBesuNonce(address, privacyGroupId, privacyUserId);
  }

  @Override
  public long determineBesuNonce(
      final Address sender, final String privacyGroupId, final String privacyUserId) {
    return privateNonceProvider.getNonce(
        sender, Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)));
  }

  @Override
  public Optional<TransactionProcessingResult> simulatePrivateTransaction(
      final String privacyGroupId,
      final String privacyUserId,
      final CallParameter callParams,
      final long blockNumber) {
    final Optional<TransactionProcessingResult> result =
        privateTransactionSimulator.process(privacyGroupId, callParams, blockNumber);
    return result;
  }

  @Override
  public Optional<String> buildAndSendAddPayload(
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final String privacyUserId) {
    if (isGroupAdditionTransaction(privateTransaction)) {
      final List<PrivateTransactionMetadata> privateTransactionMetadataList =
          buildTransactionMetadataList(privacyGroupId);
      if (privateTransactionMetadataList.size() > 0) {
        final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
            retrievePrivateTransactions(
                privacyGroupId, privateTransactionMetadataList, privacyUserId);
        final Bytes bytes = serializeAddToGroupPayload(privateTransactionWithMetadataList);
        final List<String> privateFor =
            getParticipantsFromParameter(privateTransaction.getPayload());
        return Optional.of(
            enclave.send(bytes.toBase64String(), privacyUserId, privateFor).getKey());
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    try {
      return findOffChainPrivacyGroupByGroupId(privacyGroupId, privacyUserId);
    } catch (final EnclaveClientException ex) {
      // An exception is thrown if the offchain group cannot be found
      LOG.debug("Offchain privacy group not found: {}", privacyGroupId);
    }
    return findOnChainPrivacyGroupByGroupId(Bytes.fromBase64String(privacyGroupId), privacyUserId);
  }

  @Override
  public Optional<PrivacyGroup> findOffChainPrivacyGroupByGroupId(
      final String privacyGroupId, final String privacyUserId) {
    return Optional.ofNullable(enclave.retrievePrivacyGroup(privacyGroupId));
  }

  @Override
  public List<PrivacyGroup> findOnChainPrivacyGroupByMembers(
      final List<String> addresses, final String privacyUserId) {
    final ArrayList<PrivacyGroup> privacyGroups = new ArrayList<>();
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage
            .getPrivacyGroupHeadBlockMap(blockchain.getChainHeadHash())
            .orElse(PrivacyGroupHeadBlockMap.empty());
    privacyGroupHeadBlockMap
        .keySet()
        .forEach(
            c -> {
              final Optional<PrivacyGroup> maybePrivacyGroup =
                  findOnChainPrivacyGroupByGroupId(c, privacyUserId);
              if (maybePrivacyGroup.isPresent()
                  && maybePrivacyGroup.get().getMembers().containsAll(addresses)) {
                privacyGroups.add(maybePrivacyGroup.get());
              }
            });
    return privacyGroups;
  }

  public Optional<PrivacyGroup> findOnChainPrivacyGroupByGroupId(
      final Bytes privacyGroupId, final String enclaveKey) {
    // get the privateFor list from the management contract
    final Optional<TransactionProcessingResult> privateTransactionSimulatorResultOptional =
        privateTransactionSimulator.process(
            privacyGroupId.toBase64String(), buildCallParams(GET_PARTICIPANTS_METHOD_SIGNATURE));

    if (privateTransactionSimulatorResultOptional.isPresent()
        && privateTransactionSimulatorResultOptional.get().isSuccessful()) {
      final RLPInput rlpInput =
          RLP.input(privateTransactionSimulatorResultOptional.get().getOutput());
      if (rlpInput.nextSize() > 0) {
        return Optional.of(
            new PrivacyGroup(
                privacyGroupId.toBase64String(),
                PrivacyGroup.Type.ONCHAIN,
                "",
                "",
                decodeList(rlpInput.raw())));
      } else {
        return Optional.empty();
      }
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<PrivacyGroup> findOnChainPrivacyGroupAndAddNewMembers(
      final Bytes privacyGroupId,
      final String privacyUserId,
      final PrivateTransaction privateTransaction) {
    // get the privateFor list from the management contract
    final Optional<TransactionProcessingResult> privateTransactionSimulatorResultOptional =
        privateTransactionSimulator.process(
            privacyGroupId.toBase64String(), buildCallParams(GET_PARTICIPANTS_METHOD_SIGNATURE));

    final List<String> members = new ArrayList<>();
    if (privateTransactionSimulatorResultOptional.isPresent()
        && privateTransactionSimulatorResultOptional.get().isSuccessful()) {
      final RLPInput rlpInput =
          RLP.input(privateTransactionSimulatorResultOptional.get().getOutput());
      if (rlpInput.nextSize() > 0) {
        members.addAll(decodeList(rlpInput.raw()));
        if (!members.contains(privacyUserId)) {
          return Optional.empty();
        }
      }
    }
    if (isGroupAdditionTransaction(privateTransaction)) {
      final List<String> participantsFromParameter =
          getParticipantsFromParameter(privateTransaction.getPayload());
      members.addAll(participantsFromParameter);
    }
    if (members.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(
          new PrivacyGroup(
              privacyGroupId.toBase64String(), PrivacyGroup.Type.ONCHAIN, "", "", members));
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

  private List<String> getParticipantsFromParameter(final Bytes input) {
    final List<String> participants = new ArrayList<>();
    final Bytes mungedParticipants = input.slice(4 + 32 + 32);
    for (int i = 0; i <= mungedParticipants.size() - 32; i += 32) {
      participants.add(mungedParticipants.slice(i, 32).toBase64String());
    }
    return participants;
  }

  private CallParameter buildCallParams(final Bytes methodCall) {
    return new CallParameter(
        Address.ZERO, ONCHAIN_PRIVACY_PROXY, 3000000, Wei.of(1000), Wei.ZERO, methodCall);
  }

  private List<PrivateTransactionMetadata> buildTransactionMetadataList(
      final Bytes privacyGroupId) {
    final List<PrivateTransactionMetadata> pmtHashes = new ArrayList<>();
    PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage
            .getPrivacyGroupHeadBlockMap(blockchain.getChainHeadHash())
            .orElse(PrivacyGroupHeadBlockMap.empty());
    if (privacyGroupHeadBlockMap.get(privacyGroupId) != null) {
      Hash blockHash = privacyGroupHeadBlockMap.get(privacyGroupId);
      while (blockHash != null) {
        pmtHashes.addAll(
            0,
            privateStateStorage
                .getPrivateBlockMetadata(blockHash, Bytes32.wrap(privacyGroupId))
                .get()
                .getPrivateTransactionMetadataList());
        blockHash = blockchain.getBlockHeader(blockHash).get().getParentHash();
        privacyGroupHeadBlockMap =
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHash)
                .orElse(PrivacyGroupHeadBlockMap.empty());
        if (privacyGroupHeadBlockMap.get(privacyGroupId) != null) {
          blockHash = privacyGroupHeadBlockMap.get(privacyGroupId);
        } else {
          break;
        }
      }
    }
    return pmtHashes;
  }

  private List<PrivateTransactionWithMetadata> retrievePrivateTransactions(
      final Bytes32 privacyGroupId,
      final List<PrivateTransactionMetadata> privateTransactionMetadataList,
      final String privacyUserId) {
    final ArrayList<PrivateTransactionWithMetadata> privateTransactions = new ArrayList<>();
    privateStateStorage
        .getAddDataKey(privacyGroupId)
        .ifPresent(key -> privateTransactions.addAll(retrieveAddBlob(key.toBase64String())));
    for (int i = privateTransactions.size(); i < privateTransactionMetadataList.size(); i++) {
      final PrivateTransactionMetadata privateTransactionMetadata =
          privateTransactionMetadataList.get(i);
      final Transaction privateMarkerTransaction =
          blockchain
              .getTransactionByHash(privateTransactionMetadata.getPrivateMarkerTransactionHash())
              .orElseThrow();
      final ReceiveResponse receiveResponse =
          retrieveTransaction(
              privateMarkerTransaction.getPayload().slice(0, 32).toBase64String(), privacyUserId);
      final BytesValueRLPInput input =
          new BytesValueRLPInput(
              Bytes.fromBase64String(new String(receiveResponse.getPayload(), UTF_8)), false);
      input.enterList();
      privateTransactions.add(
          new PrivateTransactionWithMetadata(
              PrivateTransaction.readFrom(input), privateTransactionMetadata));
      input.leaveListLenient();
    }

    return privateTransactions;
  }

  @Override
  public boolean isGroupAdditionTransaction(final PrivateTransaction privateTransaction) {
    return privateTransaction.getTo().isPresent()
        && privateTransaction.getTo().get().equals(ONCHAIN_PRIVACY_PROXY)
        && privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(ADD_PARTICIPANTS_METHOD_SIGNATURE.toHexString());
  }

  @Override
  public Optional<Bytes> getContractCode(
      final String privacyGroupId,
      final Address contractAddress,
      final Hash blockHash,
      final String privacyUserId) {
    return privateWorldStateReader.getContractCode(privacyGroupId, blockHash, contractAddress);
  }

  @Override
  public List<PrivateTransactionWithMetadata> retrieveAddBlob(final String addDataKey) {
    final ReceiveResponse addReceiveResponse = enclave.receive(addDataKey);
    return PrivateTransactionWithMetadata.readListFromPayload(
        Bytes.wrap(Base64.getDecoder().decode(addReceiveResponse.getPayload())));
  }

  private Bytes serializeAddToGroupPayload(
      final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList) {

    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    privateTransactionWithMetadataList.forEach(
        privateTransactionWithMetadata -> privateTransactionWithMetadata.writeTo(rlpOutput));
    rlpOutput.endList();

    return rlpOutput.encoded();
  }

  private SendResponse sendRequest(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> maybePrivacyGroup) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();

    if (maybePrivacyGroup.isPresent()) {
      final PrivacyGroup privacyGroup = maybePrivacyGroup.get();
      if (privacyGroup.getType() == PrivacyGroup.Type.ONCHAIN) {
        // onchain privacy group
        final Optional<TransactionProcessingResult> result =
            privateTransactionSimulator.process(
                privateTransaction.getPrivacyGroupId().get().toBase64String(),
                buildCallParams(GET_VERSION_METHOD_SIGNATURE));
        new VersionedPrivateTransaction(privateTransaction, result).writeTo(rlpOutput);
        final List<String> onChainPrivateFor = privacyGroup.getMembers();
        return enclave.send(
            rlpOutput.encoded().toBase64String(),
            privateTransaction.getPrivateFrom().toBase64String(),
            onChainPrivateFor);
      } else if (privacyGroup.getType() == PrivacyGroup.Type.PANTHEON) {
        // offchain privacy group
        privateTransaction.writeTo(rlpOutput);
        return enclave.send(
            rlpOutput.encoded().toBase64String(),
            privacyUserId,
            privateTransaction.getPrivacyGroupId().get().toBase64String());
      } else {
        // this should not happen
        throw new RuntimeException();
      }
    }
    // legacy transaction
    final List<String> privateFor = resolveLegacyPrivateFor(privateTransaction);
    if (privateFor.isEmpty()) {
      privateFor.add(privateTransaction.getPrivateFrom().toBase64String());
    }
    privateTransaction.writeTo(rlpOutput);
    final String payload = rlpOutput.encoded().toBase64String();

    return enclave.send(payload, privateTransaction.getPrivateFrom().toBase64String(), privateFor);
  }

  private List<String> resolveLegacyPrivateFor(final PrivateTransaction privateTransaction) {
    final ArrayList<String> privateFor = new ArrayList<>();
    final boolean isLegacyTransaction = privateTransaction.getPrivateFor().isPresent();
    if (isLegacyTransaction) {
      privateFor.addAll(
          privateTransaction.getPrivateFor().get().stream()
              .map(Bytes::toBase64String)
              .collect(Collectors.toList()));
    }
    return privateFor;
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId) {
    // NO VALIDATION NEEDED
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber)
      throws MultiTenancyValidationException {
    // NO VALIDATION NEEDED
  }

  @Override
  public PrivateTransactionSimulator getTransactionSimulator() {
    return privateTransactionSimulator;
  }

  @Override
  public Optional<Hash> getStateRootByBlockNumber(
      final String privacyGroupId, final String privacyUserId, final long blockNumber) {
    return blockchain
        .getBlockByNumber(blockNumber)
        .map(
            block ->
                privateStateRootResolver.resolveLastStateRoot(
                    Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)), block.getHash()));
  }
}
