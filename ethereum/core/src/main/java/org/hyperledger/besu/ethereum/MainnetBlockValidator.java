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
package org.hyperledger.besu.ethereum;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.BlockBodyValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateGroupIdToLatestBlockWithTransactionMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.Logger;

public class MainnetBlockValidator<C> implements BlockValidator<C> {

  private static final Logger LOG = getLogger();

  private final BlockHeaderValidator<C> blockHeaderValidator;

  private final BlockBodyValidator<C> blockBodyValidator;

  private final BlockProcessor blockProcessor;
  private PrivacyParameters privacyParameters;

  private PrivateStateStorage privateStateStorage;
  private Address address;
  private Enclave enclave;

  public MainnetBlockValidator(
      final BlockHeaderValidator<C> blockHeaderValidator,
      final BlockBodyValidator<C> blockBodyValidator,
      final BlockProcessor blockProcessor,
      final PrivacyParameters privacyParameters) {
    this.blockHeaderValidator = blockHeaderValidator;
    this.blockBodyValidator = blockBodyValidator;
    this.blockProcessor = blockProcessor;
    this.privacyParameters = privacyParameters;
    setUpForPrivacy(privacyParameters);
  }

  private void setUpForPrivacy(final PrivacyParameters privacyParameters) {
    if (privacyParameters.isEnabled()) {
      privateStateStorage = privacyParameters.getPrivateStateStorage();
      address = Address.privacyPrecompiled(privacyParameters.getPrivacyAddress());
      enclave = new Enclave(privacyParameters.getEnclaveUri());
    }
  }

  @Override
  public Optional<BlockProcessingOutputs> validateAndProcessBlock(
      final ProtocolContext<C> context,
      final Block block,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final BlockHeader header = block.getHeader();

    final Optional<BlockHeader> maybeParentHeader =
        context.getBlockchain().getBlockHeader(header.getParentHash());
    if (!maybeParentHeader.isPresent()) {
      LOG.error(
          "Attempted to import block {} with hash {} but parent block {} was not present",
          header.getNumber(),
          header.getHash(),
          header.getParentHash());
      return Optional.empty();
    }
    final BlockHeader parentHeader = maybeParentHeader.get();

    if (!blockHeaderValidator.validateHeader(header, parentHeader, context, headerValidationMode)) {
      return Optional.empty();
    }

    final MutableBlockchain blockchain = context.getBlockchain();
    final Optional<MutableWorldState> maybeWorldState =
        context.getWorldStateArchive().getMutable(parentHeader.getStateRoot());
    if (!maybeWorldState.isPresent()) {
      LOG.debug(
          "Unable to process block {} because parent world state {} is not available",
          header.getNumber(),
          parentHeader.getStateRoot());
      return Optional.empty();
    }
    final MutableWorldState worldState = maybeWorldState.get();
    final BlockProcessor.Result result = blockProcessor.processBlock(blockchain, worldState, block);
    if (!result.isSuccessful()) {
      return Optional.empty();
    }

    if (privacyParameters.isEnabled()) {
      // get all groups
      final HashMap<Bytes32, Hash> privacyGroupToLatestBlockWithTransactionMap =
          Maps.newHashMap(
              privateStateStorage
                  .getPrivacyGroupToLatestBlockWithTransactionMap(block.getHeader().getParentHash())
                  .map(PrivateGroupIdToLatestBlockWithTransactionMap::getMap)
                  .orElse(Collections.emptyMap()));
      block.getBody().getTransactions().stream()
          .filter(t -> t.getTo().isPresent() && t.getTo().get().equals(address))
          .forEach(
              t -> {
                final String key = BytesValues.asBase64String(t.getData().get());
                try {
                  final ReceiveResponse receiveResponse = enclave.receive(new ReceiveRequest(key));
                  final BytesValue privacyGroupId =
                      BytesValues.fromBase64(receiveResponse.getPrivacyGroupId());
                  privacyGroupToLatestBlockWithTransactionMap.put(
                      Bytes32.wrap(privacyGroupId), block.getHash());
                } catch (final EnclaveException e) {
                  LOG.warn("Enclave does not have private transaction with key {}.", key, e);
                }
              });
      privateStateStorage
          .updater()
          .putPrivacyGroupToLatestBlockWithTransactionMap(
              block.getHash(),
              new PrivateGroupIdToLatestBlockWithTransactionMap(
                  privacyGroupToLatestBlockWithTransactionMap))
          .commit();
    }

    final List<TransactionReceipt> receipts = result.getReceipts();
    if (!blockBodyValidator.validateBody(
        context, block, receipts, worldState.rootHash(), ommerValidationMode)) {
      return Optional.empty();
    }

    return Optional.of(new BlockProcessingOutputs(worldState, receipts));
  }

  @Override
  public boolean fastBlockValidation(
      final ProtocolContext<C> context,
      final Block block,
      final List<TransactionReceipt> receipts,
      final HeaderValidationMode headerValidationMode,
      final HeaderValidationMode ommerValidationMode) {
    final BlockHeader header = block.getHeader();
    if (!blockHeaderValidator.validateHeader(header, context, headerValidationMode)) {
      return false;
    }

    if (!blockBodyValidator.validateBodyLight(context, block, receipts, ommerValidationMode)) {
      return false;
    }
    return true;
  }
}
