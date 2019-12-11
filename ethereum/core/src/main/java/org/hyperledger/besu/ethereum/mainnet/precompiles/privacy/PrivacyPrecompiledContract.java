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
import static org.hyperledger.besu.ethereum.util.PrivacyUtil.generateLegacyGroup;

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
          "0x608060405234801561001057600080fd5b50600436106100415760003560e01c80630b0235be146100465780633659cfe6146100c9578063f744b0891461010d575b600080fd5b6100726004803603602081101561005c57600080fd5b81019080803590602001909291905050506101e7565b6040518080602001828103825283818151815260200191508051906020019060200280838360005b838110156100b557808201518184015260208101905061009a565b505050509050019250505060405180910390f35b61010b600480360360208110156100df57600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919050505061033d565b005b6101cd6004803603604081101561012357600080fd5b81019080803590602001909291908035906020019064010000000081111561014a57600080fd5b82018360208201111561015c57600080fd5b8035906020019184602083028401116401000000008311171561017e57600080fd5b919080806020026020016040519081016040528093929190818152602001838360200280828437600081840152601f19601f8201169050808301925050505050505091929192905050506103a3565b604051808215151515815260200191505060405180910390f35b606060008060009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff16630b0235be846040518263ffffffff1660e01b81526004018082815260200191505060006040518083038186803b15801561026057600080fd5b505afa158015610274573d6000803e3d6000fd5b505050506040513d6000823e3d601f19601f82011682018060405250602081101561029e57600080fd5b81019080805160405193929190846401000000008211156102be57600080fd5b838201915060208201858111156102d457600080fd5b82518660208202830111640100000000821117156102f157600080fd5b8083526020830192505050908051906020019060200280838360005b8381101561032857808201518184015260208101905061030d565b50505050905001604052505050915050919050565b8073ffffffffffffffffffffffffffffffffffffffff166000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16141561039757600080fd5b6103a0816104aa565b50565b6000806000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690508073ffffffffffffffffffffffffffffffffffffffff1663f744b08985856040518363ffffffff1660e01b81526004018083815260200180602001828103825283818151815260200191508051906020019060200280838360005b83811015610440578082015181840152602081019050610425565b505050509050019350505050602060405180830381600087803b15801561046657600080fd5b505af115801561047a573d6000803e3d6000fd5b505050506040513d602081101561049057600080fd5b810190808051906020019092919050505091505092915050565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505056fea265627a7a7231582053536d4e32439b8e6dcb33011b2b505025bb6d2837f08ad6260f73b33a5011f464736f6c634300050c0032");

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

    final BytesValue privacyGroupId =
        privateTransaction.getPrivacyGroupId().isPresent()
            ? privateTransaction.getPrivacyGroupId().get()
            : generateLegacyGroup(
                privateTransaction.getPrivateFrom(), privateTransaction.getPrivateFor().get());

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
          UInt256.of(0), UInt256.wrap(Bytes32.leftPad(managementContractAddress)));
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
