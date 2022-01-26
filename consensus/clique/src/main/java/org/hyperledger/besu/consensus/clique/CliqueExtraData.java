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
package org.hyperledger.besu.consensus.clique;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the data structure stored in the extraData field of the BlockHeader used when
 * operating under an Clique consensus mechanism.
 */
public class CliqueExtraData implements ParsedExtraData {
  private static final Logger LOG = LoggerFactory.getLogger(CliqueExtraData.class);
  public static final int EXTRA_VANITY_LENGTH = 32;

  private final Bytes vanityData;
  private final List<Address> validators;
  private final Optional<SECPSignature> proposerSeal;
  private final Supplier<Address> proposerAddress;

  public CliqueExtraData(
      final Bytes vanityData,
      final SECPSignature proposerSeal,
      final List<Address> validators,
      final BlockHeader header) {

    checkNotNull(vanityData);
    checkNotNull(validators);
    checkNotNull(header);
    checkArgument(vanityData.size() == EXTRA_VANITY_LENGTH);

    this.vanityData = vanityData;
    this.proposerSeal = Optional.ofNullable(proposerSeal);
    this.validators = validators;
    proposerAddress =
        Suppliers.memoize(() -> CliqueBlockHashing.recoverProposerAddress(header, this));
  }

  public static Bytes createWithoutProposerSeal(
      final Bytes vanityData, final List<Address> validators) {
    return CliqueExtraData.encodeUnsealed(vanityData, validators);
  }

  public static CliqueExtraData decode(final BlockHeader header) {
    final Object inputExtraData = header.getParsedExtraData();
    if (inputExtraData instanceof CliqueExtraData) {
      return (CliqueExtraData) inputExtraData;
    }
    LOG.warn(
        "Expected a CliqueExtraData instance but got {}. Reparsing required.",
        inputExtraData != null ? inputExtraData.getClass().getName() : "null");
    return decodeRaw(header);
  }

  static CliqueExtraData decodeRaw(final BlockHeader header) {
    final Bytes input = header.getExtraData();
    if (input.size() < EXTRA_VANITY_LENGTH + SECPSignature.BYTES_REQUIRED) {
      throw new IllegalArgumentException(
          "Invalid Bytes supplied - too short to produce a valid Clique Extra Data object.");
    }

    final int validatorByteCount =
        input.size() - EXTRA_VANITY_LENGTH - SECPSignature.BYTES_REQUIRED;
    if ((validatorByteCount % Address.SIZE) != 0) {
      throw new IllegalArgumentException("Bytes is of invalid size - i.e. contains unused bytes.");
    }

    final Bytes vanityData = input.slice(0, EXTRA_VANITY_LENGTH);
    final List<Address> validators =
        extractValidators(input.slice(EXTRA_VANITY_LENGTH, validatorByteCount));

    final int proposerSealStartIndex = input.size() - SECPSignature.BYTES_REQUIRED;
    final SECPSignature proposerSeal = parseProposerSeal(input.slice(proposerSealStartIndex));

    return new CliqueExtraData(vanityData, proposerSeal, validators, header);
  }

  public synchronized Address getProposerAddress() {
    return proposerAddress.get();
  }

  private static SECPSignature parseProposerSeal(final Bytes proposerSealRaw) {
    return proposerSealRaw.isZero()
        ? null
        : SignatureAlgorithmFactory.getInstance().decodeSignature(proposerSealRaw);
  }

  private static List<Address> extractValidators(final Bytes validatorsRaw) {
    final List<Address> result = Lists.newArrayList();
    final int countValidators = validatorsRaw.size() / Address.SIZE;
    for (int i = 0; i < countValidators; i++) {
      final int startIndex = i * Address.SIZE;
      result.add(Address.wrap(validatorsRaw.slice(startIndex, Address.SIZE)));
    }
    return result;
  }

  public Bytes encode() {
    return encode(vanityData, validators, proposerSeal);
  }

  public static Bytes encodeUnsealed(final Bytes vanityData, final List<Address> validators) {
    return encode(vanityData, validators, Optional.empty());
  }

  private static Bytes encode(
      final Bytes vanityData,
      final List<Address> validators,
      final Optional<SECPSignature> proposerSeal) {
    final Bytes validatorData = Bytes.concatenate(validators.toArray(new Bytes[0]));
    return Bytes.concatenate(
        vanityData,
        validatorData,
        proposerSeal
            .map(SECPSignature::encodedBytes)
            .orElse(Bytes.wrap(new byte[SECPSignature.BYTES_REQUIRED])));
  }

  public Bytes getVanityData() {
    return vanityData;
  }

  public Optional<SECPSignature> getProposerSeal() {
    return proposerSeal;
  }

  public List<Address> getValidators() {
    return validators;
  }

  public static String createGenesisExtraDataString(final List<Address> validators) {
    return CliqueExtraData.createWithoutProposerSeal(Bytes.wrap(new byte[32]), validators)
        .toString();
  }
}
