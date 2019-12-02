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

import static org.hyperledger.besu.crypto.Hash.keccak256;
import static org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver.EMPTY_ROOT_HASH;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.DefaultEvmAccount;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyPrecompiledContract extends AbstractPrecompiledContract {

  private static final BytesValue PROXY_PRECOMPILED_CODE =
      BytesValue.fromHexString(
          "0x608060405234801561001057600080fd5b50600436106100415760003560e01c80630b0235be146100465780633659cfe6146100b3578063f744b089146100db575b600080fd5b6100636004803603602081101561005c57600080fd5b5035610199565b60408051602080825283518183015283519192839290830191858101910280838360005b8381101561009f578181015183820152602001610087565b505050509050019250505060405180910390f35b6100d9600480360360208110156100c957600080fd5b50356001600160a01b03166102b9565b005b610185600480360360408110156100f157600080fd5b8135919081019060408101602082013564010000000081111561011357600080fd5b82018360208201111561012557600080fd5b8035906020019184602083028401116401000000008311171561014757600080fd5b9190808060200260200160405190810160405280939291908181526020018383602002808284376000920191909152509295506102f7945050505050565b604080519115158252519081900360200190f35b600154604080516305811adf60e11b81526004810184905290516060926001600160a01b0316918291630b0235be91602480820192600092909190829003018186803b1580156101e857600080fd5b505afa1580156101fc573d6000803e3d6000fd5b505050506040513d6000823e601f3d908101601f19168201604052602081101561022557600080fd5b810190808051604051939291908464010000000082111561024557600080fd5b90830190602082018581111561025a57600080fd5b825186602082028301116401000000008211171561027757600080fd5b82525081516020918201928201910280838360005b838110156102a457818101518382015260200161028c565b50505050905001604052505050915050919050565b6000546001600160a01b031633146102d057600080fd5b6001546001600160a01b03828116911614156102eb57600080fd5b6102f4816103bc565b50565b6001546040805163f744b08960e01b815260048101858152602482019283528451604483015284516000946001600160a01b031693849363f744b0899389938993919260640190602080860191028083838d5b8381101561036257818101518382015260200161034a565b505050509050019350505050602060405180830381600087803b15801561038857600080fd5b505af115801561039c573d6000803e3d6000fd5b505050506040513d60208110156103b257600080fd5b5051949350505050565b600180546001600160a01b0319166001600160a01b039290921691909117905556fea265627a7a72315820d1dcbb78ed14b21a5b50ade34bee9fc8297c7c6a94d385468e41387d65a635ea64736f6c634300050c0032");

  private final Enclave enclave;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateStorage privateStateStorage;
  private final PrivateStateRootResolver privateStateRootResolver;
  private PrivateTransactionProcessor privateTransactionProcessor;

  private static final Logger LOG = LogManager.getLogger();

  public PrivacyPrecompiledContract(
      final GasCalculator gasCalculator, final PrivacyParameters privacyParameters) {
    this(
        gasCalculator,
        privacyParameters.getEnclave(),
        privacyParameters.getPrivateWorldStateArchive(),
        privacyParameters.getPrivateStateStorage());
  }

  PrivacyPrecompiledContract(
      final GasCalculator gasCalculator,
      final Enclave enclave,
      final WorldStateArchive worldStateArchive,
      final PrivateStateStorage privateStateStorage) {
    super("Privacy", gasCalculator);
    this.enclave = enclave;
    this.privateWorldStateArchive = worldStateArchive;
    this.privateStateStorage = privateStateStorage;
    this.privateStateRootResolver = new PrivateStateRootResolver(privateStateStorage);
  }

  public void setPrivateTransactionProcessor(
      final PrivateTransactionProcessor privateTransactionProcessor) {
    this.privateTransactionProcessor = privateTransactionProcessor;
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return Gas.of(0L);
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final ProcessableBlockHeader currentBlockHeader = messageFrame.getBlockHeader();
    if (!BlockHeader.class.isAssignableFrom(currentBlockHeader.getClass())) {
      LOG.info("Privacy works with BlockHeader only!.");
      return BytesValue.EMPTY;
    }
    final Hash currentBlockHash = ((BlockHeader) currentBlockHeader).getHash();

    final String key = BytesValues.asBase64String(input);
    final ReceiveRequest receiveRequest = new ReceiveRequest(key);

    final ReceiveResponse receiveResponse;
    try {
      receiveResponse = enclave.receive(receiveRequest);
    } catch (final Exception e) {
      LOG.debug("Enclave does not have private transaction payload with key {}", key, e);
      return BytesValue.EMPTY;
    }

    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(BytesValues.fromBase64(receiveResponse.getPayload()), false);
    final PrivateTransaction privateTransaction = PrivateTransaction.readFrom(bytesValueRLPInput);
    final WorldUpdater publicWorldState = messageFrame.getWorldState();

    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage.getPrivacyGroupHeadBlockMap(currentBlockHash).orElseThrow();

    final Blockchain currentBlockchain = messageFrame.getBlockchain();

    // FIXME: will need to get the group id somewhere else maybe I think
    final BytesValue privacyGroupId = BytesValues.fromBase64(receiveResponse.getPrivacyGroupId());

    LOG.trace(
        "Processing private transaction {} in privacy group {}",
        privateTransaction.getHash(),
        privacyGroupId);

    final Hash lastRootHash =
        privateStateRootResolver.resolveLastStateRoot(privacyGroupId, currentBlockHash);

    final MutableWorldState disposablePrivateState =
        privateWorldStateArchive.getMutable(lastRootHash).get();

    final WorldUpdater privateWorldStateUpdater = disposablePrivateState.updater();

    // TODO Here we are setting up the this state if this was the first transaction
    if (lastRootHash.equals(EMPTY_ROOT_HASH)) {
      // this is the first transaction for the privacy group
      final Address managementContractAddress =
          Address.privateContractAddress(privateTransaction.getSender(), 0, privacyGroupId);
      final DefaultEvmAccount proxyPrecompile =
          privateWorldStateUpdater.createAccount(Address.PRIVACY_PROXY);
      final MutableAccount mutableProxyPrecompiled = proxyPrecompile.getMutable();
      // this is the code for the proxy which has no constructor
      mutableProxyPrecompiled.setCode(PROXY_PRECOMPILED_CODE);
      // manually set the management contract address so the proxy can trust it
      mutableProxyPrecompiled.setStorageValue(
          UInt256.of(0), UInt256.wrap(Bytes32.leftPad(privateTransaction.getSender())));
      mutableProxyPrecompiled.setStorageValue(
          UInt256.of(1), UInt256.wrap(Bytes32.leftPad(managementContractAddress)));
    }

    final PrivateTransactionProcessor.Result result =
        privateTransactionProcessor.processTransaction(
            currentBlockchain,
            publicWorldState,
            privateWorldStateUpdater,
            currentBlockHeader,
            privateTransaction,
            messageFrame.getMiningBeneficiary(),
            new DebugOperationTracer(TraceOptions.DEFAULT),
            messageFrame.getBlockHashLookup(),
            privacyGroupId);

    if (result.isInvalid() || !result.isSuccessful()) {
      LOG.error(
          "Failed to process private transaction {}: {}",
          privateTransaction.getHash(),
          result.getValidationResult().getErrorMessage());
      return BytesValue.EMPTY;
    }

    if (messageFrame.isPersistingState()) {
      LOG.trace(
          "Persisting private state {} for privacyGroup {}",
          disposablePrivateState.rootHash(),
          privacyGroupId);
      privateWorldStateUpdater.commit();
      disposablePrivateState.persist();

      final PrivateStateStorage.Updater privateStateUpdater = privateStateStorage.updater();
      final PrivateBlockMetadata privateBlockMetadata =
          privateStateStorage
              .getPrivateBlockMetadata(currentBlockHash, Bytes32.wrap(privacyGroupId))
              .orElseGet(PrivateBlockMetadata::empty);
      privateBlockMetadata.addPrivateTransactionMetadata(
          new PrivateTransactionMetadata(
              messageFrame.getTransactionHash(), disposablePrivateState.rootHash()));
      privateStateUpdater.putPrivateBlockMetadata(
          Bytes32.wrap(currentBlockHash.getByteArray()),
          Bytes32.wrap(privacyGroupId),
          privateBlockMetadata);

      final Bytes32 txHash = keccak256(RLP.encode(privateTransaction::writeTo));
      final LogSeries logs = result.getLogs();
      if (!logs.isEmpty()) {
        privateStateUpdater.putTransactionLogs(txHash, result.getLogs());
      }
      if (result.getRevertReason().isPresent()) {
        privateStateUpdater.putTransactionRevertReason(txHash, result.getRevertReason().get());
      }

      privateStateUpdater.putTransactionStatus(
          txHash,
          BytesValue.of(
              result.getStatus() == PrivateTransactionProcessor.Result.Status.SUCCESSFUL ? 1 : 0));
      privateStateUpdater.putTransactionResult(txHash, result.getOutput());

      // TODO: this map could be passed through from @PrivacyBlockProcessor and saved once at the
      // end of block processing
      if (!privacyGroupHeadBlockMap.contains(Bytes32.wrap(privacyGroupId), currentBlockHash)) {
        privacyGroupHeadBlockMap.put(Bytes32.wrap(privacyGroupId), currentBlockHash);
        privateStateUpdater.putPrivacyGroupHeadBlockMap(
            currentBlockHash, new PrivacyGroupHeadBlockMap(privacyGroupHeadBlockMap));
      }
      privateStateUpdater.commit();
    }

    return result.getOutput();
  }
}
