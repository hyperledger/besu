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

import static org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement.GET_PARTICIPANTS_METHOD_SIGNATURE;
import static org.hyperledger.besu.ethereum.privacy.group.OnChainGroupManagement.GET_VERSION_METHOD_SIGNATURE;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/*
 This contract is an abstraction on top of the privacy group management smart contract.
 TODO: there are a few places on OnChainPrivacyPrecompiledContract that we could use this class instead of a transaction simulator there.
*/
public class OnchainPrivacyGroupContract {

  private final PrivateTransactionSimulator privateTransactionSimulator;

  public OnchainPrivacyGroupContract(
      final PrivateTransactionSimulator privateTransactionSimulator) {
    this.privateTransactionSimulator = privateTransactionSimulator;
  }

  public Optional<PrivacyGroup> getPrivacyGroupById(final String privacyGroupId) {
    return getPrivacyGroup(privacyGroupId, Optional.empty(), Optional.empty());
  }

  public Optional<PrivacyGroup> getPrivacyGroupByIdAndBlockNumber(
      final String privacyGroupId, final Optional<Long> blockNumber) {
    return getPrivacyGroup(privacyGroupId, Optional.empty(), blockNumber);
  }

  public Optional<PrivacyGroup> getPrivacyGroupByIdAndBlockHash(
      final String privacyGroupId, final Optional<Hash> blockHash) {
    return getPrivacyGroup(privacyGroupId, blockHash, Optional.empty());
  }

  private Optional<PrivacyGroup> getPrivacyGroup(
      final String privacyGroupId,
      final Optional<Hash> blockHash,
      final Optional<Long> blockNumber) {

    final CallParameter callParams = buildCallParams(GET_PARTICIPANTS_METHOD_SIGNATURE);
    final Optional<TransactionProcessingResult> result;

    if (blockHash.isPresent()) {
      result = privateTransactionSimulator.process(privacyGroupId, callParams, blockHash.get());
    } else if (blockNumber.isPresent()) {
      result = privateTransactionSimulator.process(privacyGroupId, callParams, blockNumber.get());
    } else {
      result = privateTransactionSimulator.process(privacyGroupId, callParams);
    }
    return readPrivacyGroupFromResult(privacyGroupId, result);
  }

  private Optional<PrivacyGroup> readPrivacyGroupFromResult(
      final String privacyGroupId, final Optional<TransactionProcessingResult> result) {
    if (result.isEmpty()) {
      return Optional.empty();
    }

    if (!result.get().isSuccessful()) {
      return Optional.empty();
    }

    final RLPInput rlpInput = RLP.input(result.get().getOutput());
    if (rlpInput.nextSize() > 0) {
      final PrivacyGroup privacyGroup =
          new PrivacyGroup(
              privacyGroupId, PrivacyGroup.Type.ONCHAIN, "", "", decodeList(rlpInput.raw()));
      return Optional.of(privacyGroup);
    } else {
      return Optional.empty();
    }
  }

  public Optional<Bytes32> getVersion(final String privacyGroupId, final Optional<Hash> blockHash) {
    final CallParameter callParams = buildCallParams(GET_VERSION_METHOD_SIGNATURE);
    final Optional<TransactionProcessingResult> result;

    if (blockHash.isPresent()) {
      result = privateTransactionSimulator.process(privacyGroupId, callParams, blockHash.get());
    } else {
      result = privateTransactionSimulator.process(privacyGroupId, callParams);
    }

    return result.map(TransactionProcessingResult::getOutput).map(Bytes32::wrap);
  }

  private CallParameter buildCallParams(final Bytes methodCall) {
    return new CallParameter(
        Address.ZERO, Address.ONCHAIN_PRIVACY_PROXY, 3000000, Wei.of(1000), Wei.ZERO, methodCall);
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
}
