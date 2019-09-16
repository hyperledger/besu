/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.perm;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractPermissioningController;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.io.IOException;

import org.web3j.protocol.core.DefaultBlockParameterName;

public class NodeSmartContractPermissioningIsAllowedTransaction implements Transaction<Boolean> {

  private static final BytesValue IS_NODE_ALLOWED_SIGNATURE =
      Hash.keccak256(BytesValue.of("enodeAllowed(bytes32,bytes32,bytes16,uint16)".getBytes(UTF_8)))
          .slice(0, 4);

  private final Address contractAddress;
  private final Node node;

  public NodeSmartContractPermissioningIsAllowedTransaction(
      final Address contractAddress, final Node node) {
    this.contractAddress = contractAddress;
    this.node = node;
  }

  @Override
  public Boolean execute(final NodeRequests node) {
    try {
      final String value =
          node.eth().ethCall(payload(), DefaultBlockParameterName.LATEST).send().getValue();
      return checkTransactionResult(BytesValue.fromHexString(value));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Checks the returned bytes from the permissioning contract call to see if it's a value we
  // understand
  static Boolean checkTransactionResult(final BytesValue result) {
    // booleans are padded to 32 bytes
    if (result.size() != 32) {
      throw new IllegalArgumentException("Unexpected result size");
    }

    // 0 is false
    if (result.compareTo(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"))
        == 0) {
      return false;
      // 1 filled to 32 bytes is true
    } else if (result.compareTo(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000001"))
        == 0) {
      return true;
      // Anything else is wrong
    } else {
      throw new IllegalStateException("Unexpected result form");
    }
  }

  private org.web3j.protocol.core.methods.request.Transaction payload() {
    final String sourceEnodeURL = ((RunnableNode) node).enodeUrl().toASCIIString();
    final BytesValue payload =
        NodeSmartContractPermissioningController.createPayload(
            IS_NODE_ALLOWED_SIGNATURE, EnodeURL.fromString(sourceEnodeURL));

    return org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
        null, null, null, null, contractAddress.toString(), payload.toString());
  }
}
