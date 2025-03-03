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
package org.hyperledger.besu.ethereum.permissioning;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.permissioning.account.TransactionPermissioningProvider;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.BaseUInt256Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller that can read from a smart contract that exposes the permissioning call
 * transactionAllowed(address,address,uint256,uint256,uint256,bytes)
 */
public class TransactionSmartContractPermissioningController
    implements TransactionPermissioningProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(TransactionSmartContractPermissioningController.class);

  private final Address contractAddress;
  private final TransactionSimulator transactionSimulator;

  // full function signature for connection allowed call
  private static final String FUNCTION_SIGNATURE =
      "transactionAllowed(address,address,uint256,uint256,uint256,bytes)";
  // hashed function signature for connection allowed call
  private static final Bytes FUNCTION_SIGNATURE_HASH = hashSignature(FUNCTION_SIGNATURE);
  private final Counter checkCounterPermitted;
  private final Counter checkCounter;
  private final Counter checkCounterUnpermitted;

  // The first 4 bytes of the hash of the full textual signature of the function is used in
  // contract calls to determine the function being called
  private static Bytes hashSignature(final String signature) {
    return Hash.keccak256(Bytes.of(signature.getBytes(UTF_8))).slice(0, 4);
  }

  // True from a contract is 1 filled to 32 bytes
  private static final Bytes TRUE_RESPONSE =
      Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");
  private static final Bytes FALSE_RESPONSE =
      Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000");

  /**
   * Creates a permissioning controller attached to a blockchain
   *
   * @param contractAddress The address at which the permissioning smart contract resides
   * @param transactionSimulator A transaction simulator with attached blockchain and world state
   * @param metricsSystem The metrics provider that is to be reported to
   */
  public TransactionSmartContractPermissioningController(
      final Address contractAddress,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem) {
    this.contractAddress = contractAddress;
    this.transactionSimulator = transactionSimulator;

    this.checkCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count",
            "Number of times the transaction smart contract permissioning provider has been checked");
    this.checkCounterPermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count_permitted",
            "Number of times the transaction smart contract permissioning provider has been checked and returned permitted");
    this.checkCounterUnpermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "transaction_smart_contract_check_count_unpermitted",
            "Number of times the transaction smart contract permissioning provider has been checked and returned unpermitted");
  }

  /**
   * Check whether a given transaction should be permitted for the current head
   *
   * @param transaction The transaction to be examined
   * @return boolean of whether or not to permit the connection to occur
   */
  @Override
  public boolean isPermitted(final Transaction transaction) {
    final org.hyperledger.besu.datatypes.Hash transactionHash = transaction.getHash();
    final Address sender = transaction.getSender();

    LOG.trace("Account permissioning - Smart Contract : Checking transaction {}", transactionHash);

    this.checkCounter.inc();
    final Bytes payload = createPayload(transaction);
    final CallParameter callParams =
        new CallParameter(null, contractAddress, -1, null, null, payload);

    final Optional<Boolean> contractExists =
        transactionSimulator.doesAddressExistAtHead(contractAddress);

    if (contractExists.isPresent() && !contractExists.get()) {
      this.checkCounterPermitted.inc();
      LOG.warn(
          "Account permissioning smart contract not found at address {} in current head block. Any transaction will be allowed.",
          contractAddress);
      return true;
    }

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.processAtHead(callParams);

    if (result.isPresent()) {
      switch (result.get().result().getStatus()) {
        case INVALID:
          throw new IllegalStateException(
              "Transaction permissioning transaction found to be Invalid");
        case FAILED:
          throw new IllegalStateException(
              "Transaction permissioning transaction failed when processing");
        default:
          break;
      }
    }

    if (result.map(r -> checkTransactionResult(r.getOutput())).orElse(false)) {
      this.checkCounterPermitted.inc();
      LOG.trace(
          "Account permissioning - Smart Contract: Permitted transaction {} from {}",
          transactionHash,
          sender);
      return true;
    } else {
      this.checkCounterUnpermitted.inc();
      LOG.trace(
          "Account permissioning - Smart Contract: Rejected transaction {} from {}",
          transactionHash,
          sender);
      return false;
    }
  }

  // Checks the returned bytes from the permissioning contract call to see if it's a value we
  // understand
  public static Boolean checkTransactionResult(final Bytes result) {
    // booleans are padded to 32 bytes
    if (result.size() != 32) {
      throw new IllegalArgumentException("Unexpected result size");
    }

    // 0 is false
    if (result.equals(FALSE_RESPONSE)) {
      return false;
      // true is 1, padded to 32 bytes
    } else if (result.equals(TRUE_RESPONSE)) {
      return true;
      // Anything else is wrong
    } else {
      throw new IllegalStateException("Unexpected result form");
    }
  }

  // Assemble the bytevalue payload to call the contract
  public static Bytes createPayload(final Transaction transaction) {
    return createPayload(FUNCTION_SIGNATURE_HASH, transaction);
  }

  public static Bytes createPayload(final Bytes signature, final Transaction transaction) {
    return Bytes.concatenate(signature, encodeTransaction(transaction));
  }

  private static Bytes encodeTransaction(final Transaction transaction) {
    return Bytes.concatenate(
        encodeAddress(transaction.getSender()),
        encodeAddress(transaction.getTo()),
        transaction.getValue(),
        transaction.getGasPrice().map(BaseUInt256Value::toBytes).orElse(Bytes32.ZERO),
        encodeLong(transaction.getGasLimit()),
        encodeBytes(transaction.getPayload()));
  }

  // Case for empty address
  private static Bytes encodeAddress(final Optional<Address> address) {
    return encodeAddress(address.orElse(Address.wrap(Bytes.wrap(new byte[20]))));
  }

  // Address is the 20 bytes of value left padded by 12 bytes.
  private static Bytes encodeAddress(final Address address) {
    return Bytes.concatenate(Bytes.wrap(new byte[12]), address);
  }

  // long to uint256, 8 bytes big endian, so left padded by 24 bytes
  private static Bytes encodeLong(final long l) {
    checkArgument(l >= 0, "Unsigned value must be positive");
    final byte[] longBytes = new byte[8];
    for (int i = 0; i < 8; i++) {
      longBytes[i] = (byte) ((l >> ((7 - i) * 8)) & 0xFF);
    }
    return Bytes.concatenate(Bytes.wrap(new byte[24]), Bytes.wrap(longBytes));
  }

  // A bytes array is a uint256 of its length, then the bytes that make up its value, then pad to
  // next 32 bytes interval
  // It needs to be preceded by the bytes offset of the first dynamic parameter (192 bytes)
  private static Bytes encodeBytes(final Bytes value) {
    final Bytes dynamicParameterOffset = encodeLong(192);
    final Bytes length = encodeLong(value.size());
    final Bytes padding = Bytes.wrap(new byte[(32 - (value.size() % 32))]);
    return Bytes.concatenate(dynamicParameterOffset, length, value, padding);
  }
}
