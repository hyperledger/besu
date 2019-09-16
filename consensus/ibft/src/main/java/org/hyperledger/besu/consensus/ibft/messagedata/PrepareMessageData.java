/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft.messagedata;

import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.util.bytes.BytesValue;

public class PrepareMessageData extends AbstractIbftMessageData {

  private static final int MESSAGE_CODE = IbftV2.PREPARE;

  private PrepareMessageData(final BytesValue data) {
    super(data);
  }

  public static PrepareMessageData fromMessageData(final MessageData messageData) {
    return fromMessageData(
        messageData, MESSAGE_CODE, PrepareMessageData.class, PrepareMessageData::new);
  }

  public Prepare decode() {
    return Prepare.decode(data);
  }

  public static PrepareMessageData create(final Prepare preapare) {
    return new PrepareMessageData(preapare.encode());
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }
}
