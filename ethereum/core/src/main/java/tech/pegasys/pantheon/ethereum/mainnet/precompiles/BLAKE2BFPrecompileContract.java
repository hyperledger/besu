/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.mainnet.precompiles;

import static java.util.Arrays.copyOfRange;
import static tech.pegasys.pantheon.crypto.Blake2bfMessageDigest.Blake2bfDigest.MESSAGE_LENGTH_BYTES;

import tech.pegasys.pantheon.crypto.Hash;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.mainnet.AbstractPrecompiledContract;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// https://github.com/keep-network/go-ethereum/pull/4
public class BLAKE2BFPrecompileContract extends AbstractPrecompiledContract {

  public static Logger LOG = LogManager.getLogger();

  public BLAKE2BFPrecompileContract(final GasCalculator gasCalculator) {
    super("BLAKE2f", gasCalculator);
  }

  @Override
  public Gas gasRequirement(final BytesValue input) {
    if (input.size() != MESSAGE_LENGTH_BYTES) {
      // Input is malformed, we can't read the number of rounds.
      // Precompile can't be executed so we set its price to 0.
      return Gas.ZERO;
    }
    if ((input.get(212) & 0xFE) != 0) {
      // Input is malformed, F value can be only 0 or 1
      return Gas.ZERO;
    }

    final byte[] roundsBytes = copyOfRange(input.extractArray(), 0, 4);
    final BigInteger rounds = new BigInteger(1, roundsBytes);
    return Gas.of(rounds);
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    if (input.size() != MESSAGE_LENGTH_BYTES) {
      LOG.trace(
          "Incorrect input length.  Expected {} and got {}", MESSAGE_LENGTH_BYTES, input.size());
      return null;
    }
    if ((input.get(212) & 0xFE) != 0) {
      LOG.trace("Incorrect finalization flag, expected 0 or 1 and got {}", input.get(212));
      return null;
    }
    return Hash.blake2bf(input);
  }
}
