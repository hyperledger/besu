/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draft implementation of RIP-7212 / EIP-7951.
 *
 * <p>TODO: malleable signatures, point at infinity check, modular comparison, caching
 * implementation
 */
public class P256VerifyPrecompiledContract extends AbstractPrecompiledContract {
  private static final Logger LOG = LoggerFactory.getLogger(P256VerifyPrecompiledContract.class);
  private static final String PRECOMPILE_NAME = "P256VERIFY";
  private static final Bytes32 VALID = Bytes32.leftPad(Bytes.of(1), (byte) 0);
  private static final Bytes INVALID = Bytes.EMPTY;

  private final GasCalculator gasCalculator;
  private final SignatureAlgorithm signatureAlgorithm;

  private static final Cache<Integer, PrecompileInputResultTuple> p256VerifyCache =
      Caffeine.newBuilder().maximumSize(1000).build();

  /**
   * Instantiates a new Abstract precompiled contract.
   *
   * @param gasCalculator the gas calculator
   */
  public P256VerifyPrecompiledContract(final GasCalculator gasCalculator) {
    this(gasCalculator, new SECP256R1());
  }

  public P256VerifyPrecompiledContract(
      final GasCalculator gasCalculator, final SignatureAlgorithm signatureAlgorithm) {
    super(PRECOMPILE_NAME, gasCalculator);
    this.gasCalculator = gasCalculator;
    this.signatureAlgorithm = signatureAlgorithm;
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return gasCalculator.getP256VerifyPrecompiledContractGasCost();
  }

  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, final MessageFrame messageFrame) {
    if (input.size() != 160) {
      LOG.warn(
          "Invalid input length for P256VERIFY precompile: expected 128 bytes but got {}",
          input.size());
      return PrecompileContractResult.success(INVALID);
    }
    PrecompileInputResultTuple res;
    Integer cacheKey = null;
    if (enableResultCaching) {
      cacheKey = getCacheKey(input);
      res = p256VerifyCache.getIfPresent(cacheKey);

      if (res != null) {
        if (res.cachedInput().equals(input)) {
          cacheEventConsumer.accept(new CacheEvent(PRECOMPILE_NAME, CacheMetric.HIT));
          return res.cachedResult();
        } else {
          LOG.debug(
              "false positive p256verify {}, cache key {}, cached input: {}, input: {}",
              input.getClass().getSimpleName(),
              cacheKey,
              res.cachedInput().toHexString(),
              input.toHexString());
          cacheEventConsumer.accept(new CacheEvent(PRECOMPILE_NAME, CacheMetric.FALSE_POSITIVE));
        }
      } else {
        cacheEventConsumer.accept(new CacheEvent(PRECOMPILE_NAME, CacheMetric.MISS));
      }
    }

    final Bytes messageHash = input.slice(0, 32);
    final Bytes rBytes = input.slice(32, 32);
    final Bytes sBytes = input.slice(64, 32);
    final Bytes pubKeyBytes = input.slice(96, 64);

    try {
      // Convert r and s to BigIntegers (unsigned)
      final BigInteger r = rBytes.toUnsignedBigInteger();
      final BigInteger s = sBytes.toUnsignedBigInteger();

      // Create the signature; recID is not used in verification - use 0
      final SECPSignature signature = signatureAlgorithm.createSignature(r, s, (byte) 0);

      // Construct public key from 64-byte uncompressed format (x || y)
      final SECPPublicKey publicKey = signatureAlgorithm.createPublicKey(pubKeyBytes);

      // Perform verification TODO: implement bouncycastle malleable
      final boolean isValid = signatureAlgorithm.verifyMalleable(messageHash, signature, publicKey);

      res =
          new PrecompileInputResultTuple(
              enableResultCaching ? input.copy() : input,
              PrecompileContractResult.success(isValid ? VALID : INVALID));
      if (enableResultCaching) {
        p256VerifyCache.put(cacheKey, res);
      }
      return res.cachedResult();

    } catch (Exception e) {
      LOG.warn("P256VERIFY verification failed: {}", e.getMessage());
      System.err.println("P256VERIFY verification failed: " + e.getMessage());
      return PrecompileContractResult.success(INVALID);
    }
  }
}
