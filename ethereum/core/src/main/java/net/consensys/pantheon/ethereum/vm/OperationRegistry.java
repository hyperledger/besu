package net.consensys.pantheon.ethereum.vm;

/** Encapsulates a group of {@link Operation}s used together. */
public class OperationRegistry {

  private static final int NUM_OPERATIONS = 256;

  private final Operation[] operations;

  public OperationRegistry() {
    this.operations = new Operation[NUM_OPERATIONS];
  }

  public Operation get(final byte opcode) {
    return get(opcode & 0xff);
  }

  public Operation get(final int opcode) {
    return operations[opcode];
  }

  public void put(final int opcode, final Operation operation) {
    operations[opcode] = operation;
  }

  public Operation getOrDefault(final byte opcode, final Operation defaultOperation) {
    final Operation operation = get(opcode);

    if (operation == null) {
      return defaultOperation;
    }

    return operation;
  }
}
