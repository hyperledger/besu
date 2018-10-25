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

import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

public class IbftSignedMessageData<M extends AbstractIbftUnsignedMessageData> {

  protected final Address sender;
  protected final Signature signature;
  protected final M ibftUnsignedMessageData;

  public IbftSignedMessageData(
      final M ibftUnsignedMessageData, final Address sender, final Signature signature) {
    this.ibftUnsignedMessageData = ibftUnsignedMessageData;
    this.sender = sender;
    this.signature = signature;
  }

  public Address getSender() {
    return sender;
  }

  public Signature getSignature() {
    return signature;
  }

  public M getUnsignedMessageData() {
    return ibftUnsignedMessageData;
  }

  public void writeTo(final RLPOutput output) {

    output.startList();
    ibftUnsignedMessageData.writeTo(output);
    output.writeBytesValue(getSignature().encodedBytes());
    output.endList();
  }

  public static IbftSignedMessageData<IbftUnsignedPrePrepareMessageData>
      readIbftSignedPrePrepareMessageDataFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final IbftUnsignedPrePrepareMessageData unsignedMessageData =
        IbftUnsignedPrePrepareMessageData.readFrom(rlpInput);
    final Signature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  public static IbftSignedMessageData<IbftUnsignedPrepareMessageData>
      readIbftSignedPrepareMessageDataFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final IbftUnsignedPrepareMessageData unsignedMessageData =
        IbftUnsignedPrepareMessageData.readFrom(rlpInput);
    final Signature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  public static IbftSignedMessageData<IbftUnsignedRoundChangeMessageData>
      readIbftSignedRoundChangeMessageDataFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final IbftUnsignedRoundChangeMessageData unsignedMessageData =
        IbftUnsignedRoundChangeMessageData.readFrom(rlpInput);
    final Signature signature = readSignature(rlpInput);
    rlpInput.leaveList();

    return from(unsignedMessageData, signature);
  }

  protected static <M extends AbstractIbftUnsignedMessageData> IbftSignedMessageData<M> from(
      final M unsignedMessageData, final Signature signature) {

    final Address sender = recoverSender(unsignedMessageData, signature);

    return new IbftSignedMessageData<>(unsignedMessageData, sender, signature);
  }

  protected static Signature readSignature(final RLPInput signedMessage) {
    return signedMessage.readBytesValue(Signature::decode);
  }

  protected static Address recoverSender(
      final AbstractIbftUnsignedMessageData unsignedMessageData, final Signature signature) {

    return Util.signatureToAddress(
        signature, IbftMessageFactory.hashForSignature(unsignedMessageData));
  }
}
