package tech.pegasys.pantheon.ethereum.core;

import java.math.BigInteger;

public class AddressHelpers {

  /**
   * Creates a new address based on the provided src, and an integer offset. This is required for
   * managing ordered address lists.
   *
   * @param src The address from which a new address is to be derived.
   * @param offset The distance and polarity of the offset from src address.
   * @return A new address 'offset' away from the original src.
   */
  public static Address calculateAddressWithRespectTo(final Address src, final int offset) {

    // Need to crop the "0x" from the start of the hex string.
    final BigInteger inputValue = new BigInteger(src.toString().substring(2), 16);
    final BigInteger bigIntOffset = BigInteger.valueOf(offset);

    final BigInteger result = inputValue.add(bigIntOffset);

    return Address.fromHexString(result.toString(16));
  }

  public static Address ofValue(final int value) {
    return Address.fromHexString(String.format("%020x", value));
  }
}
