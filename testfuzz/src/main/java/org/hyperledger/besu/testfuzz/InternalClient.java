/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.testfuzz;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import org.apache.tuweni.bytes.Bytes;

class InternalClient implements FuzzingClient {
  String name;
  final EVM evm;

  public InternalClient(final String clientName) {
    this.name = clientName;
    this.evm = MainnetEVMs.futureEips(EvmConfiguration.DEFAULT);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  @SuppressWarnings("java:S2142")
  public String differentialFuzz(final String data) {
    try {
      Bytes clientData = Bytes.fromHexString(data);
      Code code = new Code(clientData);
      return "OK %d".formatted(code.getSize());
    } catch (RuntimeException e) {
      return "fail: " + e.getMessage();
    }
  }
}
