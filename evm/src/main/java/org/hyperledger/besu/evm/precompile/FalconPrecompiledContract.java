/*
 * Copyright IADB.
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
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openquantumsafe.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * note: Liboqs - random number generation defaults to /dev/urandom a better form is to use the
 * OQS_RAND_agl_openssl "OpenSSL" random number algorithm, then set the environment default engine
 * to IBRand for quantum entropy
 */
public class FalconPrecompiledContract extends AbstractPrecompiledContract {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractBLS12PrecompiledContract.class);

  private static final Bytes METHOD_ABI =
      Hash.keccak256(Bytes.of("verify(bytes,bytes,bytes)".getBytes(UTF_8))).slice(0, 4);
  // taken from liboqs C sig.h header, OQS_SIG_alg_falcon_512
  private static final String SIGNATURE_ALGORITHM = "Falcon-512";

  public FalconPrecompiledContract(final GasCalculator gasCalculator) {
    super("Falcon", gasCalculator);
  }

  @Override
  public long gasRequirement(final Bytes input) {
    long value = gasCalculator().sha256PrecompiledContractGasCost(input);
    return value;
  }

  @Override
  public Bytes compute(final Bytes methodInput, final MessageFrame messageFrame) {
    Bytes methodAbi = methodInput.slice(0, METHOD_ABI.size());
    if (!methodAbi.xor(METHOD_ABI).isZero()) {
      throw new IllegalArgumentException("Unexpected method ABI: " + methodAbi.toHexString());
    }
    Bytes input = methodInput.slice(METHOD_ABI.size());
    int signatureOffset = input.slice(0, 32).trimLeadingZeros().toInt();
    int pubKeyOffset = input.slice(32, 32).trimLeadingZeros().toInt();
    int dataOffset = input.slice(64, 32).trimLeadingZeros().toInt();

    int signatureLength = input.slice(signatureOffset, 32).trimLeadingZeros().toInt();
    int pubKeyLength = input.slice(pubKeyOffset, 32).trimLeadingZeros().toInt();
    int dataLength = input.slice(dataOffset, 32).trimLeadingZeros().toInt();

    Bytes signatureSlice = input.slice(signatureOffset + 32, signatureLength);
    Bytes pubKeySlice = input.slice(pubKeyOffset + 32, pubKeyLength);
    Bytes dataSlice = input.slice(dataOffset + 32, dataLength);

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "{} verify: signature={}, pubKey={}, data={}",
          SIGNATURE_ALGORITHM,
          signatureSlice.toHexString(),
          pubKeySlice.toHexString(),
          dataSlice.toHexString());
    }
    Signature verifier = new Signature(SIGNATURE_ALGORITHM);
    final boolean verifies =
        verifier.verify(dataSlice.toArray(), signatureSlice.toArray(), pubKeySlice.toArray());

    if (verifies) {
      LOG.debug("Signature is VALID");
      return Bytes32.leftPad(Bytes.of(0));
    } else {
      LOG.debug("Signature is INVALID");
      return Bytes32.leftPad(Bytes.of(1));
    }
  }
}
