/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm.contractvalidation;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** The Cached invalid code rule. */
public class CachedInvalidCodeRule implements ContractValidationRule {

  private final int maxEofVersion;

  /**
   * Instantiates a new Cached invalid code rule.
   *
   * @param maxEofVersion the max eof version
   */
  public CachedInvalidCodeRule(final int maxEofVersion) {
    this.maxEofVersion = maxEofVersion;
  }

  @Override
  public Optional<ExceptionalHaltReason> validate(
      final Bytes contractCode, final MessageFrame frame) {
    final Code code = CodeFactory.createCode(contractCode, maxEofVersion, false);
    if (!code.isValid()) {
      return Optional.of(ExceptionalHaltReason.INVALID_CODE);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Instantiate contract validation rule.
   *
   * @param specVersion The evm spec version
   * @return the contract validation rule
   */
  public static ContractValidationRule of(final EvmSpecVersion specVersion) {
    return new CachedInvalidCodeRule(specVersion.getMaxEofVersion());
  }
}
