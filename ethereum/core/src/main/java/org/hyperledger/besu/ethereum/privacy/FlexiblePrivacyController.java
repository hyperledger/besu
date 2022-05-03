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
package org.hyperledger.besu.ethereum.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.FlexiblePrivacyGroupContract.decodeList;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.GET_VERSION_METHOD_SIGNATURE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlexiblePrivacyController extends AbstractRestrictedPrivacyController {

  private static final Logger LOG = LoggerFactory.getLogger(FlexiblePrivacyController.class);

  private FlexiblePrivacyGroupContract flexiblePrivacyGroupContract;

  public FlexiblePrivacyController(
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

  public FlexiblePrivacyController(
      final Blockchain blockchain,
      final PrivateStateStorage privateStateStorage,
      final Enclave enclave,
      final PrivateTransactionValidator privateTransactionValidator,
      final PrivateTransactionSimulator privateTransactionSimulator,
      final PrivateNonceProvider privateNonceProvider,
      final PrivateWorldStateReader privateWorldStateReader,
      final PrivateStateRootResolver privateStateRootResolver) {
    super(
        blockchain,
        privateStateStorage,
        enclave,
        privateTransactionValidator,
        privateTransactionSimulator,
        privateNonceProvider,
        privateWorldStateReader,
        privateStateRootResolver);

    flexiblePrivacyGroupContract = new FlexiblePrivacyGroupContract(privateTransactionSimulator);
  }

  @Override
  public String createPrivateMarkerTransactionPayload(
      final PrivateTransaction privateTransaction,
      final String privacyUserId,
      final Optional<PrivacyGroup> privacyGroup) {
    LOG.trace("Storing private transaction in enclave");
    final SendResponse sendResponse = sendRequest(privateTransaction, privacyGroup);
    final String firstPart = sendResponse.getKey();
    final Optional<String> optionalSecondPart =
        buildAndSendAddPayload(
            privateTransaction,
            Bytes32.wrap(privateTransaction.getPrivacyGroupId().orElseThrow()),
            privacyUserId);

    return buildCompoundLookupId(firstPart, optionalSecondPart);
  }

  @Override
  public Optional<PrivacyGroup> findPrivacyGroupByGroupId(
      final String privacyGroupId, final String enclaveKey) {
    // get the privateFor list from the management contract
    final Optional<TransactionProcessingResult> privateTransactionSimulatorResultOptional =
        privateTransactionSimulator.process(
            privacyGroupId, buildCallParams(GET_PARTICIPANTS_METHOD_SIGNATURE));

    if (privateTransactionSimulatorResultOptional.isPresent()
        && privateTransactionSimulatorResultOptional.get().isSuccessful()) {
      final RLPInput rlpInput =
          RLP.input(privateTransactionSimulatorResultOptional.get().getOutput());
      if (rlpInput.nextSize() > 0) {
        return Optional.of(
            new PrivacyGroup(
                privacyGroupId, PrivacyGroup.Type.FLEXIBLE, "", "", decodeList(rlpInput.raw())));
      }
    }
    return Optional.empty();
  }

  @Override
  public PrivacyGroup[] findPrivacyGroupByMembers(
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
                  findPrivacyGroupByGroupId(c.toBase64String(), privacyUserId);
              if (maybePrivacyGroup.isPresent()
                  && maybePrivacyGroup.get().getMembers().containsAll(addresses)) {
                privacyGroups.add(maybePrivacyGroup.get());
              }
            });
    return privacyGroups.toArray(new PrivacyGroup[0]);
  }

  @Override
  public PrivacyGroup createPrivacyGroup(
      final List<String> addresses,
      final String name,
      final String description,
      final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using flexible privacy");
  }

  @Override
  public String deletePrivacyGroup(final String privacyGroupId, final String privacyUserId) {
    throw new PrivacyConfigurationNotSupportedException(
        "Method not supported when using flexible privacy");
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId) {
    verifyPrivacyGroupContainsPrivacyUserId(privacyGroupId, privacyUserId, Optional.empty());
  }

  @Override
  public void verifyPrivacyGroupContainsPrivacyUserId(
      final String privacyGroupId, final String privacyUserId, final Optional<Long> blockNumber) {
    final Optional<PrivacyGroup> maybePrivacyGroup =
        flexiblePrivacyGroupContract.getPrivacyGroupByIdAndBlockNumber(privacyGroupId, blockNumber);
    // IF the group exists, check member
    // ELSE member is valid if the group doesn't exist yet - this is normal for flexible privacy
    // groups
    maybePrivacyGroup.ifPresent(
        group -> {
          if (!group.getMembers().contains(privacyUserId)) {
            throw new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key");
          }
        });
  }

  private List<PrivateTransactionMetadata> buildTransactionMetadataList(
      final Bytes privacyGroupId) {
    final List<PrivateTransactionMetadata> pmtHashes = new ArrayList<>();
    PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage
            .getPrivacyGroupHeadBlockMap(blockchain.getChainHeadHash())
            .orElse(PrivacyGroupHeadBlockMap.empty());
    if (privacyGroupHeadBlockMap.containsKey(privacyGroupId)) {
      Hash blockHash = privacyGroupHeadBlockMap.get(privacyGroupId);
      while (blockHash != null) {
        pmtHashes.addAll(
            0,
            privateStateStorage
                .getPrivateBlockMetadata(blockHash, Bytes32.wrap(privacyGroupId))
                .orElseThrow()
                .getPrivateTransactionMetadataList());
        blockHash = blockchain.getBlockHeader(blockHash).orElseThrow().getParentHash();
        privacyGroupHeadBlockMap =
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHash)
                .orElse(PrivacyGroupHeadBlockMap.empty());
        if (privacyGroupHeadBlockMap.containsKey(privacyGroupId)) {
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

  private List<PrivateTransactionWithMetadata> retrieveAddBlob(final String addDataKey) {
    final ReceiveResponse addReceiveResponse = enclave.receive(addDataKey);
    return PrivateTransactionWithMetadata.readListFromPayload(
        Bytes.wrap(Base64.getDecoder().decode(addReceiveResponse.getPayload())));
  }

  private Optional<String> buildAndSendAddPayload(
      final PrivateTransaction privateTransaction,
      final Bytes32 privacyGroupId,
      final String privacyUserId) {
    if (FlexibleUtil.isGroupAdditionTransaction(privateTransaction)) {
      final List<PrivateTransactionMetadata> privateTransactionMetadataList =
          buildTransactionMetadataList(privacyGroupId);
      if (!privateTransactionMetadataList.isEmpty()) {
        final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
            retrievePrivateTransactions(
                privacyGroupId, privateTransactionMetadataList, privacyUserId);
        final Bytes bytes = serializeAddToGroupPayload(privateTransactionWithMetadataList);
        final List<String> privateFor =
            FlexibleUtil.getParticipantsFromParameter(privateTransaction.getPayload());
        return Optional.of(
            enclave.send(bytes.toBase64String(), privacyUserId, privateFor).getKey());
      }
    }
    return Optional.empty();
  }

  private String buildCompoundLookupId(
      final String privateTransactionLookupId,
      final Optional<String> maybePrivateTransactionLookupId) {
    return maybePrivateTransactionLookupId.isPresent()
        ? Bytes.concatenate(
                Bytes.fromBase64String(privateTransactionLookupId),
                Bytes.fromBase64String(maybePrivateTransactionLookupId.get()))
            .toBase64String()
        : privateTransactionLookupId;
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
      final PrivateTransaction privateTransaction, final Optional<PrivacyGroup> maybePrivacyGroup) {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();

    final PrivacyGroup privacyGroup = maybePrivacyGroup.orElseThrow();
    final Optional<TransactionProcessingResult> version =
        privateTransactionSimulator.process(
            privateTransaction.getPrivacyGroupId().orElseThrow().toBase64String(),
            buildCallParams(GET_VERSION_METHOD_SIGNATURE));
    new VersionedPrivateTransaction(privateTransaction, version).writeTo(rlpOutput);
    final List<String> flexiblePrivateFor = privacyGroup.getMembers();
    return enclave.send(
        rlpOutput.encoded().toBase64String(),
        privateTransaction.getPrivateFrom().toBase64String(),
        flexiblePrivateFor);
  }

  CallParameter buildCallParams(final Bytes methodCall) {
    return new CallParameter(
        Address.ZERO, FLEXIBLE_PRIVACY_PROXY, 3000000, Wei.of(1000), Wei.ZERO, methodCall);
  }

  ReceiveResponse retrieveTransaction(final String enclaveKey, final String privacyUserId) {
    return enclave.receive(enclaveKey, privacyUserId);
  }

  @VisibleForTesting
  public void setFlexiblePrivacyGroupContract(
      final FlexiblePrivacyGroupContract flexiblePrivacyGroupContract) {
    this.flexiblePrivacyGroupContract = flexiblePrivacyGroupContract;
  }
}
