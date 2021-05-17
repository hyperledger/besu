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
package org.hyperledger.besu.cli.subcommands.rlp;

/** Type of the RLP data to encode/decode */
public enum RLPType {
  // Enum is used to enable the listing of the possible values in PicoCLI.
  IBFT_EXTRA_DATA(new IbftExtraDataCLIAdapter()),
  QBFT_EXTRA_DATA(new QbftExtraDataCLIAdapter());

  private final JSONToRLP adapter;

  RLPType(final JSONToRLP adapter) {

    this.adapter = adapter;
  }

  public JSONToRLP getAdapter() {
    return adapter;
  }
}
