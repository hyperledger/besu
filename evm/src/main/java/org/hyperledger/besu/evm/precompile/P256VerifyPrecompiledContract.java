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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

public class P256VerifyPrecompiledContract extends AbstractPrecompiledContract {
  private final SECP256R1 p256 = new SECP256R1();
  private final Bytes32 SUCCESS = Bytes32.fromHexString("0x01");

  public P256VerifyPrecompiledContract(final GasCalculator gasCalculator) {
    super("P256VERIFY", gasCalculator);
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return gasCalculator().getP256PrecompiledContractGasCost();
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {
    final int size = input.size();
    final Bytes d = size >= 160 ? input : Bytes.wrap(input, MutableBytes.create(160 - size));

    final Bytes32 hash = Bytes32.wrap(d, 0);
    final BigInteger r = d.slice(32, 32).toUnsignedBigInteger();
    final BigInteger s = d.slice(64, 32).toUnsignedBigInteger();
    final BigInteger publicKey = d.slice(96, 64).toUnsignedBigInteger();

    return p256.verify(hash, r, s, p256.createPublicKey(publicKey))
        ? PrecompileContractResult.success(SUCCESS)
        : PrecompileContractResult.success(Bytes32.ZERO);
  }
}
