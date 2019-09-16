/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.permissioning;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.node.NodePermissioningProvider;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Controller that can read from a smart contract that exposes the permissioning call
 * connectionAllowed(bytes32,bytes32,bytes16,uint16,bytes32,bytes32,bytes16,uint16)
 */
public class NodeSmartContractPermissioningController implements NodePermissioningProvider {
  private final Address contractAddress;
  private final TransactionSimulator transactionSimulator;

  // full function signature for connection allowed call
  private static final String FUNCTION_SIGNATURE =
      "connectionAllowed(bytes32,bytes32,bytes16,uint16,bytes32,bytes32,bytes16,uint16)";
  // hashed function signature for connection allowed call
  private static final BytesValue FUNCTION_SIGNATURE_HASH = hashSignature(FUNCTION_SIGNATURE);
  private final Counter checkCounter;
  private final Counter checkCounterPermitted;
  private final Counter checkCounterUnpermitted;

  // The first 4 bytes of the hash of the full textual signature of the function is used in
  // contract calls to determine the function being called
  private static BytesValue hashSignature(final String signature) {
    return Hash.keccak256(BytesValue.of(signature.getBytes(UTF_8))).slice(0, 4);
  }

  // True from a contract is 1 filled to 32 bytes
  private static final BytesValue TRUE_RESPONSE =
      BytesValue.fromHexString(
          "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
  private static final BytesValue FALSE_RESPONSE =
      BytesValue.fromHexString(
          "0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  /**
   * Creates a permissioning controller attached to a blockchain
   *
   * @param contractAddress The address at which the permissioning smart contract resides
   * @param transactionSimulator A transaction simulator with attached blockchain and world state
   * @param metricsSystem The metrics provider that is to be reported to
   */
  public NodeSmartContractPermissioningController(
      final Address contractAddress,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem) {
    this.contractAddress = contractAddress;
    this.transactionSimulator = transactionSimulator;

    this.checkCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count",
            "Number of times the node smart contract permissioning provider has been checked");
    this.checkCounterPermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_permitted",
            "Number of times the node smart contract permissioning provider has been checked and returned permitted");
    this.checkCounterUnpermitted =
        metricsSystem.createCounter(
            BesuMetricCategory.PERMISSIONING,
            "node_smart_contract_check_count_unpermitted",
            "Number of times the node smart contract permissioning provider has been checked and returned unpermitted");
  }

  /**
   * Check whether a given connection from the source to destination enode should be permitted
   *
   * @param sourceEnode The enode url of the node initiating the connection
   * @param destinationEnode The enode url of the node receiving the connection
   * @return boolean of whether or not to permit the connection to occur
   */
  @Override
  public boolean isPermitted(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    this.checkCounter.inc();
    final BytesValue payload = createPayload(sourceEnode, destinationEnode);
    final CallParameter callParams =
        new CallParameter(null, contractAddress, -1, null, null, payload);

    final Optional<Boolean> contractExists =
        transactionSimulator.doesAddressExistAtHead(contractAddress);

    if (contractExists.isPresent() && !contractExists.get()) {
      throw new IllegalStateException("Permissioning contract does not exist");
    }

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.processAtHead(callParams);

    if (result.isPresent()) {
      switch (result.get().getResult().getStatus()) {
        case INVALID:
          throw new IllegalStateException("Permissioning transaction found to be Invalid");
        case FAILED:
          throw new IllegalStateException("Permissioning transaction failed when processing");
        default:
          break;
      }
    }

    if (result.map(r -> checkTransactionResult(r.getOutput())).orElse(false)) {
      this.checkCounterPermitted.inc();
      return true;
    } else {
      this.checkCounterUnpermitted.inc();
      return false;
    }
  }

  // Checks the returned bytes from the permissioning contract call to see if it's a value we
  // understand
  public static Boolean checkTransactionResult(final BytesValue result) {
    // booleans are padded to 32 bytes
    if (result.size() != 32) {
      throw new IllegalArgumentException("Unexpected result size");
    }

    // 0 is false
    if (result.compareTo(FALSE_RESPONSE) == 0) {
      return false;
      // 1 filled to 32 bytes is true
    } else if (result.compareTo(TRUE_RESPONSE) == 0) {
      return true;
      // Anything else is wrong
    } else {
      throw new IllegalStateException("Unexpected result form");
    }
  }

  // Assemble the bytevalue payload to call the contract
  public static BytesValue createPayload(
      final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    return createPayload(FUNCTION_SIGNATURE_HASH, sourceEnode, destinationEnode);
  }

  public static BytesValue createPayload(
      final BytesValue signature, final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    return BytesValues.concatenate(
        signature, encodeEnodeUrl(sourceEnode), encodeEnodeUrl(destinationEnode));
  }

  public static BytesValue createPayload(final BytesValue signature, final EnodeURL enodeURL) {
    return BytesValues.concatenate(signature, encodeEnodeUrl(enodeURL));
  }

  private static BytesValue encodeEnodeUrl(final EnodeURL enode) {
    return BytesValues.concatenate(
        enode.getNodeId(), encodeIp(enode.getIp()), encodePort(enode.getListeningPortOrZero()));
  }

  // As a function parameter an ip needs to be the appropriate number of bytes, big endian, and
  // filled to 32 bytes
  private static BytesValue encodeIp(final InetAddress addr) {
    // InetAddress deals with giving us the right number of bytes
    final byte[] address = addr.getAddress();
    final byte[] res = new byte[32];
    if (address.length == 4) {
      // lead with 10 bytes of 0's
      // then 2 bytes of 1's
      res[10] = (byte) 0xFF;
      res[11] = (byte) 0xFF;
      // then the ipv4
      System.arraycopy(address, 0, res, 12, 4);
    } else {
      System.arraycopy(address, 0, res, 0, address.length);
    }
    return BytesValue.wrap(res);
  }

  // The port, a uint16, needs to be 2 bytes, little endian, and filled to 32 bytes
  private static BytesValue encodePort(final Integer port) {
    final byte[] res = new byte[32];
    res[31] = (byte) ((port) & 0xFF);
    res[30] = (byte) ((port >> 8) & 0xFF);
    return BytesValue.wrap(res);
  }
}
