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
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import org.hyperledger.besu.crypto.SECP256K1.PublicKey;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.bytes.MutableBytes32;
import org.hyperledger.besu.util.bytes.MutableBytesValue;

import java.math.BigInteger;
import java.util.Optional;

public class ECRECPrecompiledContract extends AbstractPrecompiledContract {

  private static final int V_BASE = 27;

  public ECRECPrecompiledContract(final GasCalculator gasCalculator) {
    super("ECREC", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    return gasCalculator().getEcrecPrecompiledContractGasCost();
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final int size = input.size();
    final BytesValue d =
        size >= 128 ? input : BytesValue.wrap(input, MutableBytesValue.create(128 - size));
    final Bytes32 h = Bytes32.wrap(d, 0);
    // Note that the Yellow Paper defines v as the next 32 bytes (so 32..63). Yet, v is a simple
    // byte in ECDSARECOVER and the Yellow Paper is not very clear on this mismatch but it appears
    // it is simply the last byte of those 32 bytes that needs to be used. It does appear we need
    // to check the rest of the bytes are zero though.
    if (!d.slice(32, 31).isZero()) {
      return BytesValue.EMPTY;
    }

    final int recId = d.get(63) - V_BASE;
    final BigInteger r = BytesValues.asUnsignedBigInteger(d.slice(64, 32));
    final BigInteger s = BytesValues.asUnsignedBigInteger(d.slice(96, 32));

    final Signature signature;
    try {
      signature = Signature.create(r, s, (byte) recId);
    } catch (final IllegalArgumentException e) {
      return BytesValue.EMPTY;
    }

    // SECP256K1#PublicKey#recoverFromSignature throws an Illegal argument exception
    // when it is unable to recover the key. There is not a straightforward way to
    // check the arguments ahead of time to determine if the fail will happen and
    // the library needs to be updated.
    try {
      final Optional<PublicKey> recovered = PublicKey.recoverFromSignature(h, signature);
      if (!recovered.isPresent()) {
        return BytesValue.EMPTY;
      }

      final Bytes32 hashed = Hash.hash(recovered.get().getEncodedBytes());
      final MutableBytes32 result = MutableBytes32.create();
      hashed.slice(12).copyTo(result, 12);
      return result;
    } catch (final IllegalArgumentException e) {
      return BytesValue.EMPTY;
    }
  }
}
