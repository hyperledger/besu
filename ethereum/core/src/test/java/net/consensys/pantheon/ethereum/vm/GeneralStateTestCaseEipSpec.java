package net.consensys.pantheon.ethereum.vm;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.WorldState;

import java.util.function.Supplier;

public class GeneralStateTestCaseEipSpec {

  private final String eip;

  // Creating the actual transaction is expensive because the json test file does not give us the
  // transaction but rather the private key to sign, and so we have to do the signing. And we don't
  // want to do this for 22k general state tests up-front (during
  // GeneralStateReferenceTest.getTestParametersForConfig) because 1) that makes the parameters
  // generation of
  // GeneralStateReferenceTest take more than a minute, which means that much time waiting before
  // anything
  // is run, which isn't friendly and 2) this makes it harder to parallelize this step. Anyway, this
  // is why this is a supplier: calling get() actually does the signing.
  private final Supplier<Transaction> transactionSupplier;

  private final WorldState initialWorldState;

  private final Hash expectedRootHash;

  // The keccak256 hash of the RLP encoding of the log series
  private final Hash expectedLogsHash;

  private final BlockHeader blockHeader;

  GeneralStateTestCaseEipSpec(
      final String eip,
      final Supplier<Transaction> transactionSupplier,
      final WorldState initialWorldState,
      final Hash expectedRootHash,
      final Hash expectedLogsHash,
      final BlockHeader blockHeader) {
    this.eip = eip;
    this.transactionSupplier = transactionSupplier;
    this.initialWorldState = initialWorldState;
    this.expectedRootHash = expectedRootHash;
    this.expectedLogsHash = expectedLogsHash;
    this.blockHeader = blockHeader;
  }

  String eip() {
    return eip;
  }

  WorldState initialWorldState() {
    return initialWorldState;
  }

  Hash expectedRootHash() {
    return expectedRootHash;
  }

  Hash expectedLogsHash() {
    return expectedLogsHash;
  }

  Transaction transaction() {
    return transactionSupplier.get();
  }

  BlockHeader blockHeader() {
    return blockHeader;
  }
}
