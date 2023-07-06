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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.perm;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.permissioning.NodeSmartContractV2PermissioningController;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.protocol.core.DefaultBlockParameterName;

public class NodeSmartContractPermissioningConnectionIsAllowedV2Transaction
    implements Transaction<Boolean> {

  private final Address contractAddress;
  private final Node node;

  public NodeSmartContractPermissioningConnectionIsAllowedV2Transaction(
      final Address contractAddress, final Node node) {
    this.contractAddress = contractAddress;
    this.node = node;
  }

  @Override
  public Boolean execute(final NodeRequests node) {
    try {
      final String value =
          node.eth().ethCall(payload(), DefaultBlockParameterName.LATEST).send().getValue();
      return Bytes.fromHexString(value)
          .equals(NodeSmartContractV2PermissioningController.TRUE_RESPONSE);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private org.web3j.protocol.core.methods.request.Transaction payload() {
    final EnodeURL enodeURL = EnodeURLImpl.fromURI(((RunnableNode) node).enodeUrl());
    final Bytes payload = createPayload(enodeURL);

    return org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
        null, null, null, null, contractAddress.toString(), payload.toString());
  }

  private Bytes createPayload(final EnodeURL enodeUrl) {
    try {
      final String hexNodeIdString = enodeUrl.getNodeId().toUnprefixedHexString();
      final String address = enodeUrl.getIp().getHostAddress();
      final int port = enodeUrl.getListeningPortOrZero();

      final Function connectionAllowedFunction =
          FunctionEncoder.makeFunction(
              "connectionAllowed",
              List.of("string", "string", "uint16"),
              List.of(hexNodeIdString, address, port),
              Collections.emptyList());
      return Bytes.fromHexString(FunctionEncoder.encode(connectionAllowedFunction));
    } catch (Exception e) {
      throw new RuntimeException("Error calling connectionAllowed", e);
    }
  }
}
