package org.hyperledger.besu.ethereum.vm.operations;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.Hash;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@OutputTimeUnit(value = TimeUnit.MILLISECONDS)
public class Hashing20kKeccak256Benchmark {

  @Param({
    "32",
    "128",
    "256",
    "512"
  })
  private String inputSize;

  public Bytes bytes;

  @Setup
  public void setUp() {
    final Random random = new Random();
    final byte[] byteArray = new byte[Integer.parseInt(inputSize)];
    random.nextBytes(byteArray);
    bytes = Bytes.wrap(byteArray);
  }

  @Benchmark
  public void executeOperation() {
    for (int i = 0; i < 20000; i++) {
      Hash.keccak256(bytes);
    }
  }
}
