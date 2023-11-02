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
 */
package org.hyperledger.besu.evm.precompile;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.pqc.crypto.falcon.FalconParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconPublicKeyParameters;
import org.bouncycastle.pqc.crypto.falcon.FalconSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Falcon precompiled contract. */
public class FalconPrecompiledContract extends AbstractPrecompiledContract {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractBLS12PrecompiledContract.class);

  private static final Bytes METHOD_ABI =
      Hash.keccak256(Bytes.of("verify(bytes,bytes,bytes)".getBytes(UTF_8))).slice(0, 4);
  private static final String SIGNATURE_ALGORITHM = "Falcon-512";

  private final FalconSigner falconSigner = new FalconSigner();

  /**
   * Instantiates a new Falcon precompiled contract.
   *
   * @param gasCalculator the gas calculator
   */
  public FalconPrecompiledContract(final GasCalculator gasCalculator) {
    super("Falcon", gasCalculator);
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return gasCalculator().falconVerifyPrecompiledContractGasCost(input);
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes methodInput, @Nonnull final MessageFrame messageFrame) {
    Bytes methodAbi;
    try {
      methodAbi = methodInput.slice(0, METHOD_ABI.size());
      if (!methodAbi.xor(METHOD_ABI).isZero()) {
        LOG.trace("Unexpected method ABI: " + methodAbi.toHexString());
        return PrecompileContractResult.halt(
            null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
      }
    } catch (Exception e) {
      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
    Bytes signatureSlice;
    Bytes pubKeySlice;
    Bytes dataSlice;

    try {
      Bytes input = methodInput.slice(METHOD_ABI.size());
      int signatureOffset = input.slice(0, 32).trimLeadingZeros().toInt();
      int pubKeyOffset = input.slice(32, 32).trimLeadingZeros().toInt();
      int dataOffset = input.slice(64, 32).trimLeadingZeros().toInt();

      int signatureLength = input.slice(signatureOffset, 32).trimLeadingZeros().toInt();
      int pubKeyLength = input.slice(pubKeyOffset, 32).trimLeadingZeros().toInt();
      int dataLength = input.slice(dataOffset, 32).trimLeadingZeros().toInt();

      signatureSlice = input.slice(signatureOffset + 32, signatureLength);
      pubKeySlice =
          input.slice(
              pubKeyOffset + 32 + 1,
              pubKeyLength - 1); // BouncyCastle omits the first byte since it is always zero
      dataSlice = input.slice(dataOffset + 32, dataLength);
    } catch (Exception e) {
      LOG.trace("Error executing Falcon-512 precompiled contract: '{}'", "invalid input");
      return PrecompileContractResult.success(Bytes32.leftPad(Bytes.of(1)));
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "{} verify: signature={}, pubKey={}, data={}",
          SIGNATURE_ALGORITHM,
          signatureSlice.toHexString(),
          pubKeySlice.toHexString(),
          dataSlice.toHexString());
    }
    FalconPublicKeyParameters falconPublicKeyParameters =
        new FalconPublicKeyParameters(FalconParameters.falcon_512, pubKeySlice.toArray());
    falconSigner.init(false, falconPublicKeyParameters);
    final boolean verifies =
        falconSigner.verifySignature(dataSlice.toArray(), signatureSlice.toArray());

    if (verifies) {
      LOG.debug("Signature is VALID");
      return PrecompileContractResult.success(Bytes32.leftPad(Bytes.of(0)));
    } else {
      LOG.debug("Signature is INVALID");
      return PrecompileContractResult.success(Bytes32.leftPad(Bytes.of(1)));
    }
  }
}
