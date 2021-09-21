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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.mainnet.PrivateStateUtils;
import org.hyperledger.besu.evm.worldstate.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivacyPluginPrecompiledContract extends PrivacyPrecompiledContract {
  private static final Logger LOG = LogManager.getLogger();
  private final PrivacyParameters privacyParameters;

  public PrivacyPluginPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    super(gasCalculator, privacyParameters, "PluginPrivacy");
    this.privacyParameters = privacyParameters;
  }

  @Override
  public Bytes compute(final Bytes input, final MessageFrame messageFrame) {

    if (skipContractExecution(messageFrame)) {
      return Bytes.EMPTY;
    }

    final Optional<org.hyperledger.besu.plugin.data.PrivateTransaction> pluginPrivateTransaction =
        privacyParameters
            .getPrivacyService()
            .getPayloadProvider()
            .getPrivateTransactionFromPayload(messageFrame.getContextVariable(PrivateStateUtils.KEY_TRANSACTION));

    if (pluginPrivateTransaction.isEmpty()) {
      return Bytes.EMPTY;
    }

    final PrivateTransaction privateTransaction =
        PrivateTransaction.readFrom(pluginPrivateTransaction.get());

    final Bytes32 privacyGroupId = privateTransaction.determinePrivacyGroupId();
    final Hash pmtHash = messageFrame.getContextVariable(PrivateStateUtils.KEY_TRANSACTION_HASH);

    LOG.debug(
        "Processing unrestricted private transaction {} in privacy group {}",
        pmtHash,
        privacyGroupId);

    final PrivateMetadataUpdater privateMetadataUpdater = messageFrame.getContextVariable(PrivateStateUtils.KEY_PRIVATE_METADATA_UPDATER);
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
        messageFrame.getBlockHeader().getNumber());

    final TransactionProcessingResult result =
        processPrivateTransaction(
            messageFrame, privateTransaction, privacyGroupId, privateWorldStateUpdater);

    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process unrestricted private transaction {}: {}",
          pmtHash,
          result.getValidationResult().getErrorMessage());

      privateMetadataUpdater.putTransactionReceipt(pmtHash, new PrivateTransactionReceipt(result));

      return Bytes.EMPTY;
    }

    if (messageFrame.getContextVariable(PrivateStateUtils.KEY_IS_PERSISTING_PRIVATE_STATE, false)) {

      privateWorldStateUpdater.commit();
      disposablePrivateState.persist(null);

      storePrivateMetadata(
          pmtHash, privacyGroupId, disposablePrivateState, privateMetadataUpdater, result);
    }

    return result.getOutput();
  }
}
