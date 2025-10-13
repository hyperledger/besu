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
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.code.CodeV1;
import org.hyperledger.besu.evm.code.EOFLayout;
import org.hyperledger.besu.evm.code.EOFLayout.EOFContainerMode;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import org.apache.tuweni.bytes.Bytes;

class InternalClient implements FuzzingClient {
  String name;
  final EVM evm;

  public InternalClient(final String clientName) {
    this.name = clientName;
    this.evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
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
      Code code = evm.getCodeUncached(clientData);
      if (code.getEofVersion() < 1) {
        return "err: legacy EVM";
      } else if (!code.isValid()) {
        return "err: " + ((CodeInvalid) code).getInvalidReason();
      } else {
        EOFLayout layout = ((CodeV1) code).getEofLayout();
        if (EOFContainerMode.INITCODE.equals(layout.containerMode().get())) {
          return "err: initcode container when runtime mode expected";
        }
        return "OK %d/%d/%d"
            .formatted(
                layout.getCodeSectionCount(), layout.getSubcontainerCount(), layout.dataLength());
      }
    } catch (RuntimeException e) {
      return "fail: " + e.getMessage();
    }
  }
}
