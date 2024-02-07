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

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulatorResult;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.net.InetAddress;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

/**
 * Controller that can read from a smart contract that exposes the permissioning call
 * connectionAllowed(bytes32,bytes32,bytes16,uint16,bytes32,bytes32,bytes16,uint16)
 */
public class NodeSmartContractPermissioningController
    extends AbstractNodeSmartContractPermissioningController {

  // full function signature for connection allowed call
  private static final String FUNCTION_SIGNATURE =
      "connectionAllowed(bytes32,bytes32,bytes16,uint16,bytes32,bytes32,bytes16,uint16)";
  // hashed function signature for connection allowed call
  private static final Bytes FUNCTION_SIGNATURE_HASH = hashSignature(FUNCTION_SIGNATURE);

  // The first 4 bytes of the hash of the full textual signature of the function is used in
  // contract calls to determine the function being called
  private static Bytes hashSignature(final String signature) {
    return Hash.keccak256(Bytes.of(signature.getBytes(UTF_8))).slice(0, 4);
  }

  // True from a contract is 1 filled to 32 bytes
  private static final Bytes TRUE_RESPONSE =
      Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
  private static final Bytes FALSE_RESPONSE =
      Bytes.fromHexString("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  public NodeSmartContractPermissioningController(
      final Address contractAddress,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem) {
    super(contractAddress, transactionSimulator, metricsSystem);
  }

  @Override
  boolean checkSmartContractRules(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    final Bytes payload = createPayload(sourceEnode, destinationEnode);
    final CallParameter callParams = buildCallParameters(payload);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.processAtHead(callParams);

    if (result.isPresent()) {
      switch (result.get().result().getStatus()) {
        case INVALID:
          throw new IllegalStateException("Permissioning transaction found to be Invalid");
        case FAILED:
          throw new IllegalStateException("Permissioning transaction failed when processing");
        default:
          break;
      }
    }

    return result.map(r -> checkTransactionResult(r.getOutput())).orElse(false);
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
      // 1 filled to 32 bytes is true
    } else if (result.equals(TRUE_RESPONSE)) {
      return true;
      // Anything else is wrong
    } else {
      throw new IllegalStateException("Unexpected result form");
    }
  }

  // Assemble the bytevalue payload to call the contract
  private static Bytes createPayload(final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    return createPayload(FUNCTION_SIGNATURE_HASH, sourceEnode, destinationEnode);
  }

  @VisibleForTesting
  public static Bytes createPayload(
      final Bytes signature, final EnodeURL sourceEnode, final EnodeURL destinationEnode) {
    return Bytes.concatenate(
        signature, encodeEnodeUrl(sourceEnode), encodeEnodeUrl(destinationEnode));
  }

  @VisibleForTesting
  public static Bytes createPayload(final Bytes signature, final EnodeURL enodeURL) {
    return Bytes.concatenate(signature, encodeEnodeUrl(enodeURL));
  }

  private static Bytes encodeEnodeUrl(final EnodeURL enode) {
    return Bytes.concatenate(
        enode.getNodeId(), encodeIp(enode.getIp()), encodePort(enode.getListeningPortOrZero()));
  }

  // As a function parameter an ip needs to be the appropriate number of bytes, big endian, and
  // filled to 32 bytes
  private static Bytes encodeIp(final InetAddress addr) {
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
    return Bytes.wrap(res);
  }

  // The port, a uint16, needs to be 2 bytes, little endian, and filled to 32 bytes
  private static Bytes encodePort(final Integer port) {
    final byte[] res = new byte[32];
    res[31] = (byte) ((port) & 0xFF);
    res[30] = (byte) ((port >> 8) & 0xFF);
    return Bytes.wrap(res);
  }
}
