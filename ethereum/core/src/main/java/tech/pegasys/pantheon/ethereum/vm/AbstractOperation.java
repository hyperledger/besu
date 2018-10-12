package net.consensys.pantheon.ethereum.vm;

/**
 * All {@link Operation} implementations should inherit from this class to get the setting of some
 * members for free.
 */
public abstract class AbstractOperation implements Operation {
  private final int opcode;
  private final String name;
  private final int stackItemsConsumed;
  private final int stackItemsProduced;
  private final boolean updatesProgramCounter;
  private final int opSize;
  private final GasCalculator gasCalculator;

  public AbstractOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final boolean updatesProgramCounter,
      final int opSize,
      final GasCalculator gasCalculator) {
    this.opcode = opcode & 0xff;
    this.name = name;
    this.stackItemsConsumed = stackItemsConsumed;
    this.stackItemsProduced = stackItemsProduced;
    this.updatesProgramCounter = updatesProgramCounter;
    this.opSize = opSize;
    this.gasCalculator = gasCalculator;
  }

  protected GasCalculator gasCalculator() {
    return gasCalculator;
  }

  @Override
  public int getOpcode() {
    return opcode;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getStackItemsConsumed() {
    return stackItemsConsumed;
  }

  @Override
  public int getStackItemsProduced() {
    return stackItemsProduced;
  }

  @Override
  public int getStackSizeChange() {
    return stackItemsProduced - stackItemsConsumed;
  }

  @Override
  public boolean getUpdatesProgramCounter() {
    return updatesProgramCounter;
  }

  @Override
  public int getOpSize() {
    return opSize;
  }
}
