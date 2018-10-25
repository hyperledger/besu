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

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.util.Optional;

public class IbftMessageFactory {
  private final KeyPair validatorKeyPair;

  public IbftMessageFactory(final KeyPair validatorKeyPair) {
    this.validatorKeyPair = validatorKeyPair;
  }

  public IbftSignedMessageData<IbftUnsignedPrepareMessageData> createIbftSignedPrepareMessageData(
      final ConsensusRoundIdentifier roundIdentifier, final Hash digest) {

    IbftUnsignedPrepareMessageData prepareUnsignedMessageData =
        new IbftUnsignedPrepareMessageData(roundIdentifier, digest);

    return createSignedMessage(prepareUnsignedMessageData);
  }

  public IbftSignedMessageData<IbftUnsignedRoundChangeMessageData>
      createIbftSignedRoundChangeMessageData(
          final ConsensusRoundIdentifier roundIdentifier,
          final Optional<IbftPreparedCertificate> preparedCertificate) {

    IbftUnsignedRoundChangeMessageData prepareUnsignedMessageData =
        new IbftUnsignedRoundChangeMessageData(roundIdentifier, preparedCertificate);

    return createSignedMessage(prepareUnsignedMessageData);
  }

  private <M extends AbstractIbftUnsignedMessageData> IbftSignedMessageData<M> createSignedMessage(
      final M ibftUnsignedMessage) {
    final Signature signature = sign(ibftUnsignedMessage, validatorKeyPair);

    return new IbftSignedMessageData<>(
        ibftUnsignedMessage, Util.publicKeyToAddress(validatorKeyPair.getPublicKey()), signature);
  }

  static Hash hashForSignature(final AbstractIbftUnsignedMessageData unsignedMessageData) {
    return Hash.hash(
        BytesValues.concatenate(
            BytesValues.ofUnsignedByte(unsignedMessageData.getMessageType()),
            unsignedMessageData.encoded()));
  }

  private static Signature sign(
      final AbstractIbftUnsignedMessageData unsignedMessageData, final KeyPair nodeKeys) {

    return SECP256K1.sign(hashForSignature(unsignedMessageData), nodeKeys);
  }
}
