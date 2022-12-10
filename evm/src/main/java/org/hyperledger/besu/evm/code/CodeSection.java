package org.hyperledger.besu.evm.code;

import org.apache.tuweni.bytes.Bytes;

//// java17 convert to record
public final class CodeSection {
  final Bytes code;
  final int inputs;
  final int outputs;
  final int maxStackHeight;

  public CodeSection(
      final Bytes code, final int inputs, final int outputs, final int maxStackHeight) {
    this.code = code;
    this.inputs = inputs;
    this.outputs = outputs;
    this.maxStackHeight = maxStackHeight;
  }

  public Bytes getCode() {
    return code;
  }

  public int getInputs() {
    return inputs;
  }

  public int getOutputs() {
    return outputs;
  }

  public int getMaxStackHeight() {
    return maxStackHeight;
  }
}
