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
package org.hyperledger.besu.ethereum.eth.messages;

public final class EthPV63 {

  // Eth63 includes all message types from Eth62 (so see EthPV62 for where the live)

  // Plus some new message types
  public static final int GET_NODE_DATA = 0x0D;

  public static final int NODE_DATA = 0x0E;

  public static final int GET_RECEIPTS = 0x0F;

  public static final int RECEIPTS = 0x10;

  private EthPV63() {
    // Holder for constants only
  }
}
