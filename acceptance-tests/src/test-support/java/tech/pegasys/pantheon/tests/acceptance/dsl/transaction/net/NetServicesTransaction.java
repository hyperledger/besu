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
package tech.pegasys.pantheon.tests.acceptance.dsl.transaction.net;

import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.NodeRequests;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

import java.util.Map;

import org.web3j.protocol.core.Request;

public class NetServicesTransaction implements Transaction<Map<String, Map<String, String>>> {

  NetServicesTransaction() {}

  @Override
  public Map<String, Map<String, String>> execute(final NodeRequests requestFactories) {
    CustomRequestFactory.NetServicesResponse netServicesResponse = null;
    try {
      final CustomRequestFactory netServicesJsonRpcRequestFactory = requestFactories.custom();
      final Request<?, CustomRequestFactory.NetServicesResponse> request =
          netServicesJsonRpcRequestFactory.netServices();

      netServicesResponse = request.send();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    return netServicesResponse.getResult();
  }
}
