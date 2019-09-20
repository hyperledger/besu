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

public final class EthPV62 {

  public static final int STATUS = 0x00;

  public static final int NEW_BLOCK_HASHES = 0x01;

  public static final int TRANSACTIONS = 0x02;

  public static final int GET_BLOCK_HEADERS = 0x03;

  public static final int BLOCK_HEADERS = 0x04;

  public static final int GET_BLOCK_BODIES = 0x05;

  public static final int BLOCK_BODIES = 0x06;

  public static final int NEW_BLOCK = 0X07;

  private EthPV62() {
    // Holder for constants only
  }
}
