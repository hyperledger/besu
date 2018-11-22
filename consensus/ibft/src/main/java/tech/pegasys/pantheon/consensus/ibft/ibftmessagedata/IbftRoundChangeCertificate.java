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
package tech.pegasys.pantheon.consensus.ibft.ibftmessagedata;

import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import java.util.Collection;

public class IbftRoundChangeCertificate {

  private final Collection<IbftSignedMessageData<IbftUnsignedRoundChangeMessageData>>
      ibftRoundChangeMessages;

  public IbftRoundChangeCertificate(
      final Collection<IbftSignedMessageData<IbftUnsignedRoundChangeMessageData>>
          ibftRoundChangeMessages) {
    this.ibftRoundChangeMessages = ibftRoundChangeMessages;
  }

  public static IbftRoundChangeCertificate readFrom(final RLPInput rlpInput) {
    final Collection<IbftSignedMessageData<IbftUnsignedRoundChangeMessageData>>
        ibftRoundChangeMessages;

    rlpInput.enterList();
    ibftRoundChangeMessages =
        rlpInput.readList(IbftSignedMessageData::readIbftSignedRoundChangeMessageDataFrom);
    rlpInput.leaveList();

    return new IbftRoundChangeCertificate(ibftRoundChangeMessages);
  }

  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    rlpOutput.writeList(ibftRoundChangeMessages, IbftSignedMessageData::writeTo);
    rlpOutput.endList();
  }

  public Collection<IbftSignedMessageData<IbftUnsignedRoundChangeMessageData>>
      getIbftRoundChangeMessages() {
    return ibftRoundChangeMessages;
  }
}
