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
package tech.pegasys.pantheon.ethereum.mainnet;

import static tech.pegasys.pantheon.ethereum.mainnet.EthHash.EPOCH_LENGTH;

import tech.pegasys.pantheon.crypto.Hash;
import tech.pegasys.pantheon.crypto.MessageDigestFactory;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DirectAcyclicGraphSeed {

  public static final ThreadLocal<MessageDigest> KECCAK_256 =
      ThreadLocal.withInitial(
          () -> {
            try {
              return MessageDigestFactory.create(Hash.KECCAK256_ALG);
            } catch (final NoSuchAlgorithmException ex) {
              throw new IllegalStateException(ex);
            }
          });

  public static byte[] dagSeed(final long block) {
    final byte[] seed = new byte[32];
    if (Long.compareUnsigned(block, EPOCH_LENGTH) >= 0) {
      final MessageDigest keccak256 = KECCAK_256.get();
      for (int i = 0; i < Long.divideUnsigned(block, EPOCH_LENGTH); ++i) {
        keccak256.update(seed);
        try {
          keccak256.digest(seed, 0, seed.length);
        } catch (final DigestException ex) {
          throw new IllegalStateException(ex);
        }
      }
    }
    return seed;
  }
}
