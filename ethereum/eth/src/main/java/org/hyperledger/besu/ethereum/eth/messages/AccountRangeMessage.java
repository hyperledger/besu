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

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

public final class AccountRangeMessage extends AbstractSnapMessageData {

  public static AccountRangeMessage readFrom(final MessageData message) {
    if (message instanceof AccountRangeMessage) {
      return (AccountRangeMessage) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.ACCOUNT_RANGE) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a AccountRangeMessage.", code));
    }
    return new AccountRangeMessage(message.getData());
  }

  public static AccountRangeMessage create() {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    // TODO In a following PR it will be necessary to create the AccountRange message correctly as
    // it is described in the snapsync specification
    tmp.endList();
    return new AccountRangeMessage(tmp.encoded());
  }

  private AccountRangeMessage(final Bytes data) {
    super(data);
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    tmp.writeBigIntegerScalar(requestId);
    tmp.endList();
    return tmp.encoded();
  }

  @Override
  public int getCode() {
    return SnapV1.ACCOUNT_RANGE;
  }
}
