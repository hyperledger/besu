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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.crypto.altbn128.AltBn128Point;
import org.hyperledger.besu.crypto.altbn128.Fq;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP196;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The AltBN128Mul precompiled contract. */
public class AltBN128MulPrecompiledContract extends AbstractAltBnPrecompiledContract {
  private static final Logger LOG = LoggerFactory.getLogger(AltBN128MulPrecompiledContract.class);

  private static final int PARAMETER_LENGTH = 96;
  private static final String PRECOMPILE_NAME = "AltBN128Mul";

  private static final BigInteger MAX_N =
      new BigInteger(
          "115792089237316195423570985008687907853269984665640564039457584007913129639935");

  private static final Bytes POINT_AT_INFINITY = Bytes.repeat((byte) 0, 64);
  private final long gasCost;
  private static final Cache<Integer, PrecompileInputResultTuple> bnMulCache =
      Caffeine.newBuilder().maximumSize(1000).build();

  private AltBN128MulPrecompiledContract(final GasCalculator gasCalculator, final long gasCost) {
    super(
        PRECOMPILE_NAME,
        gasCalculator,
        LibGnarkEIP196.EIP196_MUL_OPERATION_RAW_VALUE,
        PARAMETER_LENGTH);
    this.gasCost = gasCost;
  }

  /**
   * Create Byzantium AltBN128Mul precompiled contract.
   *
   * @param gasCalculator the gas calculator
   * @return the alt bn 128 mul precompiled contract
   */
  public static AltBN128MulPrecompiledContract byzantium(final GasCalculator gasCalculator) {
    return new AltBN128MulPrecompiledContract(gasCalculator, 40_000L);
  }

  /**
   * Create Istanbul AltBN128Mul precompiled contract.
   *
   * @param gasCalculator the gas calculator
   * @return the alt bn 128 mul precompiled contract
   */
  public static AltBN128MulPrecompiledContract istanbul(final GasCalculator gasCalculator) {
    return new AltBN128MulPrecompiledContract(gasCalculator, 6_000L);
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return gasCost;
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {

    if (input.size() >= 64 && input.slice(0, 64).equals(POINT_AT_INFINITY)) {
      return new PrecompileContractResult(
          POINT_AT_INFINITY, false, MessageFrame.State.COMPLETED_SUCCESS, Optional.empty());
    }

    PrecompileInputResultTuple res;
    Integer cacheKey = null;
    if (enableResultCaching) {
      cacheKey = getCacheKey(input);
      res = bnMulCache.getIfPresent(cacheKey);
      if (res != null) {
        if (res.cachedInput().equals(input)) {
          cacheEventConsumer.accept(new CacheEvent(PRECOMPILE_NAME, CacheMetric.HIT));
          return res.cachedResult();
        } else {
          LOG.debug(
              "false positive altbn128Mul {}, cache key {}, cached input: {}, input: {}",
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

    if (useNative) {
      res =
          new PrecompileInputResultTuple(
              enableResultCaching ? input.copy() : input, computeNative(input, messageFrame));
    } else {
      res =
          new PrecompileInputResultTuple(
              enableResultCaching ? input.copy() : input, computeDefault(input));
    }
    if (cacheKey != null) {
      bnMulCache.put(cacheKey, res);
    }
    return res.cachedResult();
  }

  @Nonnull
  private static PrecompileContractResult computeDefault(final Bytes input) {
    final BigInteger x = extractParameter(input, 0, 32);
    final BigInteger y = extractParameter(input, 32, 32);
    final BigInteger n = extractParameter(input, 64, 32);

    final AltBn128Point p = new AltBn128Point(Fq.create(x), Fq.create(y));
    if (!p.isOnCurve() || n.compareTo(MAX_N) > 0) {
      return PrecompileContractResult.halt(
          null, Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));
    }
    final AltBn128Point product = p.multiply(n);

    final Bytes xResult = product.getX().toBytes();
    final Bytes yResult = product.getY().toBytes();
    final MutableBytes result = MutableBytes.create(64);
    xResult.copyTo(result, 32 - xResult.size());
    yResult.copyTo(result, 64 - yResult.size());

    return PrecompileContractResult.success(result);
  }

  private static BigInteger extractParameter(
      final Bytes input, final int offset, final int length) {
    if (offset > input.size() || length == 0) {
      return BigInteger.ZERO;
    }
    final byte[] raw = Arrays.copyOfRange(input.toArrayUnsafe(), offset, offset + length);
    return new BigInteger(1, raw);
  }
}
