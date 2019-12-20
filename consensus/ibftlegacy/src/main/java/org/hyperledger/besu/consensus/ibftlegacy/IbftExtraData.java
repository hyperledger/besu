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
package org.hyperledger.besu.consensus.ibftlegacy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an IBFT consensus mechanism.
 */
public class IbftExtraData implements ParsedExtraData {
  private static final Logger LOG = LogManager.getLogger();

  public static final int EXTRA_VANITY_LENGTH = 32;

  private final Bytes vanityData;
  private final Collection<Signature> seals;
  private final Signature proposerSeal;
  private final Collection<Address> validators;

  public IbftExtraData(
      final Bytes vanityData,
      final Collection<Signature> seals,
      final Signature proposerSeal,
      final Collection<Address> validators) {

    checkNotNull(vanityData);
    checkNotNull(seals);
    checkNotNull(validators);

    this.vanityData = vanityData;
    this.seals = seals;
    this.proposerSeal = proposerSeal;
    this.validators = validators;
  }

  public static IbftExtraData decode(final BlockHeader header) {
    final Object inputExtraData = header.getParsedExtraData();
    if (inputExtraData instanceof IbftExtraData) {
      return (IbftExtraData) inputExtraData;
    }
    LOG.warn(
        "Expected a IbftExtraData instance but got {}. Reparsing required.",
        inputExtraData != null ? inputExtraData.getClass().getName() : "null");
    return decodeRaw(header.internalGetExtraData());
  }

  static IbftExtraData decodeRaw(final Bytes input) {
    checkArgument(
        input.size() > EXTRA_VANITY_LENGTH,
        "Invalid Bytes supplied - too short to produce a valid IBFT Extra Data object.");

    final Bytes vanityData = input.slice(0, EXTRA_VANITY_LENGTH);

    final Bytes rlpData = input.slice(EXTRA_VANITY_LENGTH);
    final RLPInput rlpInput = new BytesValueRLPInput(rlpData, false);

    rlpInput.enterList(); // This accounts for the "root node" which contains IBFT data items.
    final Collection<Address> validators = rlpInput.readList(Address::readFrom);
    final Signature proposerSeal = parseProposerSeal(rlpInput);
    final Collection<Signature> seals = rlpInput.readList(rlp -> Signature.decode(rlp.readBytes()));
    rlpInput.leaveList();

    return new IbftExtraData(vanityData, seals, proposerSeal, validators);
  }

  private static Signature parseProposerSeal(final RLPInput rlpInput) {
    final Bytes data = rlpInput.readBytes();
    return data.isZero() ? null : Signature.decode(data);
  }

  public Bytes encode() {
    final BytesValueRLPOutput encoder = new BytesValueRLPOutput();
    encoder.startList();
    encoder.writeList(validators, (validator, rlp) -> rlp.writeBytes(validator));
    if (proposerSeal != null) {
      encoder.writeBytes(proposerSeal.encodedBytes());
    } else {
      encoder.writeNull();
    }
    encoder.writeList(seals, (committer, rlp) -> rlp.writeBytes(committer.encodedBytes()));
    encoder.endList();

    return Bytes.wrap(vanityData, encoder.encoded());
  }

  // Accessors
  public Bytes getVanityData() {
    return vanityData;
  }

  public Collection<Signature> getSeals() {
    return seals;
  }

  public Signature getProposerSeal() {
    return proposerSeal;
  }

  public Collection<Address> getValidators() {
    return validators;
  }
}
