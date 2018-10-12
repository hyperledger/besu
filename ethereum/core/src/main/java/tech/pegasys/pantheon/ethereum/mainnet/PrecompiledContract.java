package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.util.bytes.BytesValue;

/**
 * A pre-compiled contract.
 *
 * <p>It corresponds to one of the function defined in Appendix E of the Yellow Paper (rev.
 * a91c29c).
 */
public interface PrecompiledContract {

  /**
   * Returns the pre-compiled contract name.
   *
   * @return the pre-compiled contract name
   */
  String getName();

  /**
   * Gas requirement for the contract.
   *
   * @param input the input for the pre-compiled contract (on which the gas requirement may or may
   *     not depend).
   * @return the gas requirement (cost) for the pre-compiled contract.
   */
  Gas gasRequirement(BytesValue input);

  /**
   * Executes the pre-compiled contract.
   *
   * @param input the input for the pre-compiled contract.
   * @return the output of the pre-compiled contract.
   */
  BytesValue compute(BytesValue input);
}
