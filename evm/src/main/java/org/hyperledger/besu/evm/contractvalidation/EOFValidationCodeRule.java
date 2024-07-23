/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.contractvalidation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates code is properly EOF formatted if it has the correct magic bytes. This supplants
 * PrefixRule.
 */
public class EOFValidationCodeRule implements ContractValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(EOFValidationCodeRule.class);

  final int maxEofVersion;

  private EOFValidationCodeRule(final int maxEofVersion) {
    this.maxEofVersion = maxEofVersion;
  }

  /**
   * Runs eof validation rules as per the various EIPs covering it.
   *
   * @param contractCode the contract code to validate
   * @param frame the message frame to use for context
   * @param evm The EVM against which the validation should be considered against
   * @return Either an empty optional on success, or an optional containing one of the invalid
   *     reasons.
   */
  @Override
  public Optional<ExceptionalHaltReason> validate(
      final Bytes contractCode, final MessageFrame frame, final EVM evm) {
    Code code = evm.getCode(Hash.hash(contractCode), contractCode);
    if (!code.isValid()) {
      LOG.trace("EOF Validation Error: {}", ((CodeInvalid) code).getInvalidReason());
      return Optional.of(ExceptionalHaltReason.INVALID_CODE);
    }

    if (frame.getCode().getEofVersion() != code.getEofVersion()) {
      LOG.trace(
          "Cannot deploy older eof versions: initcode version - {} runtime code version - {}",
          frame.getCode().getEofVersion(),
          code.getEofVersion());
      return Optional.of(ExceptionalHaltReason.EOF_CREATE_VERSION_INCOMPATIBLE);
    }

    return Optional.empty();
  }

  /**
   * Create EOF validation.
   *
   * @param maxEofVersion Maximum EOF version to validate
   * @return The EOF validation contract validation rule.
   * @deprecated use {@link #from(EVM)}
   */
  @Deprecated(forRemoval = true, since = "24.6.1")
  public static ContractValidationRule of(final int maxEofVersion) {
    return new EOFValidationCodeRule(maxEofVersion);
  }

  /**
   * Create EOF validation.
   *
   * @param evm The EVM for which we are enforcing the rule
   * @return The EOF validation contract validation rule.
   */
  public static ContractValidationRule from(final EVM evm) {
    return new EOFValidationCodeRule(evm.getMaxEOFVersion());
  }
}
