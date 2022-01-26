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

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Collection;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an IBFT consensus mechanism.
 */
public class IbftExtraData implements ParsedExtraData {
  private static final Logger LOG = LoggerFactory.getLogger(IbftExtraData.class);

  public static final int EXTRA_VANITY_LENGTH = 32;
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);

  private final Bytes vanityData;
  private final Collection<SECPSignature> seals;
  private final SECPSignature proposerSeal;
  private final Collection<Address> validators;

  public IbftExtraData(
      final Bytes vanityData,
      final Collection<SECPSignature> seals,
      final SECPSignature proposerSeal,
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
    return decodeRaw(header.getExtraData());
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
    final SECPSignature proposerSeal = parseProposerSeal(rlpInput);
    final Collection<SECPSignature> seals =
        rlpInput.readList(rlp -> SIGNATURE_ALGORITHM.get().decodeSignature(rlp.readBytes()));
    rlpInput.leaveList();

    return new IbftExtraData(vanityData, seals, proposerSeal, validators);
  }

  private static SECPSignature parseProposerSeal(final RLPInput rlpInput) {
    final Bytes data = rlpInput.readBytes();
    return data.isZero() ? null : SIGNATURE_ALGORITHM.get().decodeSignature(data);
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

  public Collection<SECPSignature> getSeals() {
    return seals;
  }

  public SECPSignature getProposerSeal() {
    return proposerSeal;
  }

  public Collection<Address> getValidators() {
    return validators;
  }
}
