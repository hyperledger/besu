package org.hyperledger.besu.evm.precompile;

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.crypto.Ed25519;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.cip.cip8.COSESign1;
import org.apache.tuweni.bytes.Bytes;
import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint8;

public class L1MsgVerifyPrecompiledContract extends AbstractPrecompiledContract {
  private final DefaultFunctionEncoder encoder = new DefaultFunctionEncoder();

  private final Bytes verifyFnSignature = calculateSignatureHash("verify(uint8,bytes,bytes,bytes)");

  private final List<TypeReference<?>> verifyFnInputTypes =
      Arrays.asList(
          new TypeReference<Uint8>() {},
          new TypeReference<DynamicBytes>() {},
          new TypeReference<DynamicBytes>() {},
          new TypeReference<DynamicBytes>() {});

  public L1MsgVerifyPrecompiledContract(final GasCalculator gasCalculator) {
    super("L1MsgVerify", gasCalculator);
  }

  /** TODO: Has to be revisited. Gas should be deduced step by step depending on the input. */
  @Override
  public long gasRequirement(final Bytes input) {
    return 1_000L;
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes callData, @Nonnull final MessageFrame messageFrame) {
    final Bytes signature = callData.slice(0, 4);

    if (!signature.equals(verifyFnSignature)) return error("Invalid function signature");

    final Bytes input = callData.slice(4);

    return verify(input);
  }

  protected PrecompileContractResult verify(final Bytes input) {
    try {
      final List<?> args =
          FunctionReturnDecoder.decode(input.toHexString(), Utils.convert(verifyFnInputTypes));

      final Uint8 l1Type = (Uint8) args.get(0);
      if (l1Type.getValue().intValue() != 1) return error("Invalid L1Type");

      final byte[] rawSign1 = ((DynamicBytes) args.get(1)).getValue();
      final byte[] key = ((DynamicBytes) args.get(2)).getValue();
      final byte[] l1Address = ((DynamicBytes) args.get(3)).getValue();

      final boolean signatureValid = Ed25519.verify(rawSign1, key);

      COSESign1 sign1 = COSESign1.deserialize(rawSign1);

      final String bech32AddressFromSign1 = getBech32AddressFromSign1(sign1);

      final boolean addressValid = bech32AddressFromSign1.equals(new String(l1Address, "UTF-8"));

      final Bytes output =
          Bytes.fromHexString(
              encoder.encodeParameters(
                  List.of(
                      new Bool(signatureValid && addressValid),
                      new DynamicBytes(sign1.payload()))));

      return PrecompileContractResult.success(output);
    } catch (Exception e) {
      return error(e.toString());
    }
  }

  protected String getBech32AddressFromSign1(final COSESign1 sign1) {
    byte[] address =
        sign1.headers()._protected().getAsHeaderMap().otherHeaderAsBytes(Ed25519.ADDRESS_LABEL);

    return new Address(address).toBech32();
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
