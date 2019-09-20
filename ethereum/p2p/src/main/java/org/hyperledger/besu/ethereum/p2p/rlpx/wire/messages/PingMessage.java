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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages;

public final class PingMessage extends EmptyMessage {

  private static final PingMessage INSTANCE = new PingMessage();

  public static PingMessage get() {
    return INSTANCE;
  }

  private PingMessage() {}

  @Override
  public int getCode() {
    return WireMessageCodes.PING;
  }

  @Override
  public String toString() {
    return "PingMessage{data=''}";
  }
}
