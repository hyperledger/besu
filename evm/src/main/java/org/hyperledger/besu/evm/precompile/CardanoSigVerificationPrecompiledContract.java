/*
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

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.crypto.Ed25519;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Utf8String;

public class CardanoSigVerificationPrecompiledContract extends AbstractPrecompiledContract {
  private final DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();

  private final Bytes verifySignature = calculateSignatureHash("verify(bytes,bytes)");
  private final List<TypeReference<?>> verifyInputTypes =
      Arrays.asList(new TypeReference<DynamicBytes>() {}, new TypeReference<DynamicBytes>() {});

  private final Bytes getPayloadFromSign1Signature =
      calculateSignatureHash("getPayloadFromSign1(bytes)");
  private final List<TypeReference<?>> getPayloadFromSign1Types =
      Arrays.asList(new TypeReference<DynamicBytes>() {});

  private final Bytes getBech32AddressFromSign1Signature =
      calculateSignatureHash("getBech32AddressFromSign1(bytes)");
  private final List<TypeReference<?>> getBech32AddressFromSign1Types =
      Arrays.asList(new TypeReference<DynamicBytes>() {});

  public CardanoSigVerificationPrecompiledContract(final GasCalculator gasCalculator) {
    super("CardanoSigVerification", gasCalculator);
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return 100;
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {
    try {
      return router(input);
    } catch (Exception e) {
      return error(e.toString());
    }
  }

  protected PrecompileContractResult router(final Bytes callData) {
    final Bytes signature = callData.slice(0, 4);
    final Bytes input = callData.slice(4);

    if (signature.equals(verifySignature)) return verify(input);
    if (signature.equals(getPayloadFromSign1Signature)) return getPayloadFromSign1(input);
    if (signature.equals(getBech32AddressFromSign1Signature))
      return getBech32AddressFromSign1(input);

    return error("Invalid function signature");
  }

  protected PrecompileContractResult verify(final Bytes input) {
    try {
      final List<DynamicBytes> args =
          FunctionReturnDecoder.decode(input.toHexString(), Utils.convert(verifyInputTypes))
              .stream()
              .map(arg -> (DynamicBytes) arg)
              .collect(Collectors.toList());

      final byte[] sign1 = args.get(0).getValue();
      final byte[] key = args.get(1).getValue();

      final boolean verified = Ed25519.verify(sign1, key);

      final MutableBytes output = MutableBytes.create(32);

      return PrecompileContractResult.success(verified ? output.increment() : output);
    } catch (Exception e) {
      return error(e.toString());
    }
  }

  protected PrecompileContractResult getPayloadFromSign1(final Bytes input) {
    try {
      final List<DynamicBytes> args =
          FunctionReturnDecoder.decode(input.toHexString(), Utils.convert(getPayloadFromSign1Types))
              .stream()
              .map(arg -> (DynamicBytes) arg)
              .collect(Collectors.toList());

      final byte[] rawSign1 = args.get(0).getValue();

      final COSESign1 sign1 = COSESign1.deserialize(rawSign1);

      final Bytes output =
          Bytes.fromHexString(encoder.encodeParameters(List.of(new DynamicBytes(sign1.payload()))));

      return PrecompileContractResult.success(output);
    } catch (Exception e) {
      return error(e.toString());
    }
  }

  protected PrecompileContractResult getBech32AddressFromSign1(final Bytes input) {
    try {
      final List<DynamicBytes> args =
          FunctionReturnDecoder.decode(
                  input.toHexString(), Utils.convert(getBech32AddressFromSign1Types))
              .stream()
              .map(arg -> (DynamicBytes) arg)
              .collect(Collectors.toList());

      final byte[] rawSign1 = args.get(0).getValue();

      final COSESign1 sign1 = COSESign1.deserialize(rawSign1);

      byte[] address =
          sign1.headers()._protected().getAsHeaderMap().otherHeaderAsBytes(Ed25519.ADDRESS_LABEL);

      String bech32Address = new Address(address).toBech32();

      final Bytes output =
          Bytes.fromHexString(encoder.encodeParameters(List.of(new Utf8String(bech32Address))));

      return PrecompileContractResult.success(output);
    } catch (Exception e) {
      return error(e.toString());
    }
  }

  protected Bytes calculateSignatureHash(final String signature) {
    final Bytes hash = keccak256(Bytes.wrap(signature.getBytes(StandardCharsets.US_ASCII)));
    return hash.slice(0, 4);
  }

  protected PrecompileContractResult error(final String reason) {
    final Bytes signature = calculateSignatureHash("Error(string)").slice(0, 4);
    final Bytes data =
        Bytes.fromHexString(encoder.encodeParameters(Arrays.asList(new Utf8String(reason))));

    return PrecompileContractResult.revert(Bytes.wrap(signature, data));
  }
}
