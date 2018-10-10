package net.consensys.pantheon.ethereum.mainnet.headervalidationrules;

import static org.apache.logging.log4j.LogManager.getLogger;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import java.util.function.Function;

import org.apache.logging.log4j.Logger;

public class ConstantFieldValidationRule<T> implements DetachedBlockHeaderValidationRule {
  private static final Logger LOG = getLogger();
  private final T expectedValue;
  private final Function<BlockHeader, T> accessor;
  private final String fieldName;

  public ConstantFieldValidationRule(
      final String fieldName, final Function<BlockHeader, T> accessor, final T expectedValue) {
    this.expectedValue = expectedValue;
    this.accessor = accessor;
    this.fieldName = fieldName;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final T actualValue = accessor.apply(header);
    if (!actualValue.equals(expectedValue)) {
      LOG.trace(
          "{} failed validation. Actual != Expected ({} != {}).",
          fieldName,
          actualValue,
          expectedValue);
      return false;
    }
    return true;
  }
}
