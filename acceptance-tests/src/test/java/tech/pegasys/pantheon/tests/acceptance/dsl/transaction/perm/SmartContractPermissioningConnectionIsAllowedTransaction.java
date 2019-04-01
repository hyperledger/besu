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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm;

import static tech.pegasys.pantheon.ethereum.permissioning.SmartContractPermissioningController.checkTransactionResult;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.permissioning.SmartContractPermissioningController;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.RunnableNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.JsonRequestFactories;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.io.IOException;

import org.web3j.protocol.core.DefaultBlockParameterName;

public class SmartContractPermissioningConnectionIsAllowedTransaction
    implements Transaction<Boolean> {

  private static final String IS_CONNECTION_ALLOWED_IPV4_SIGNATURE = "0x9b9d2bce";

  private final Address contractAddress;
  private final Node source;
  private final Node target;

  public SmartContractPermissioningConnectionIsAllowedTransaction(
      final Address contractAddress, final Node source, final Node target) {
    this.contractAddress = contractAddress;
    this.source = source;
    this.target = target;
  }

  @Override
  public Boolean execute(final JsonRequestFactories node) {
    try {
      final String value =
          node.eth().ethCall(payload(), DefaultBlockParameterName.LATEST).send().getValue();
      return checkTransactionResult(BytesValue.fromHexString(value));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private org.web3j.protocol.core.methods.request.Transaction payload() {
    final String sourceEnodeURL = ((RunnableNode) source).enodeUrl().toASCIIString();
    final String targetEnodeURL = ((RunnableNode) target).enodeUrl().toASCIIString();
    final BytesValue payload =
        SmartContractPermissioningController.createPayload(
            BytesValue.fromHexString(IS_CONNECTION_ALLOWED_IPV4_SIGNATURE),
            new EnodeURL(sourceEnodeURL),
            new EnodeURL(targetEnodeURL));

    return org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
        null, null, null, null, contractAddress.toString(), payload.toString());
  }
}
