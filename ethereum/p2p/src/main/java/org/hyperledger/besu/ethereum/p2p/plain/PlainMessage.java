/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.p2p.plain;

import org.apache.tuweni.bytes.Bytes;

public class PlainMessage {

  private final MessageType messageType;
  private final int code;
  private final Bytes data;

  public PlainMessage(final MessageType messageType, final byte[] data) {
    this(messageType, Bytes.wrap(data));
  }

  public PlainMessage(final MessageType messageType, final Bytes data) {
    this(messageType, -1, data);
  }

  public PlainMessage(final MessageType messageType, final int code, final Bytes data) {
    super();
    this.messageType = messageType;
    this.code = code;
    this.data = data;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public Bytes getData() {
    return data;
  }

  public int getCode() {
    return code;
  }
}
