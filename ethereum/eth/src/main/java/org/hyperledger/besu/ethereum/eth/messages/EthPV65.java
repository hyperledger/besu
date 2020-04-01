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

public final class EthPV65 {

  public static final int NEW_POOLED_TRANSACTION_HASHES = 0x08;

  public static final int GET_POOLED_TRANSACTIONS = 0x09;

  public static final int POOLED_TRANSACTIONS = 0x0A;

  private EthPV65() {
    // Holder for constants only
  }
}
