package org.hyperledger.besu.ethereum.core;

import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.CODE_OFFSET;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.HEADER_STORAGE_OFFSET;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.MAIN_STORAGE_OFFSET;
import static org.hyperledger.besu.ethereum.trie.verkle.util.Parameters.VERKLE_NODE_WIDTH;

import org.hyperledger.besu.datatypes.Address;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.tuweni.units.bigints.UInt256;

public class AccessWitness implements org.hyperledger.besu.datatypes.AccessWitness {

  private static final long WITNESS_BRANCH_READ_COST = 1900;
  private static final long WITNESS_CHUNK_READ_COST = 200;
  private static final long WITNESS_BRANCH_WRITE_COST = 3000;
  private static final long WITNESS_CHUNK_WRITE_COST = 500;
  private static final long WITNESS_CHUNK_FILL_COST = 6200;

  private static final UInt256 zeroTreeIndex = UInt256.ZERO;
  private static final byte AccessWitnessReadFlag = 1;
  private static final byte AccessWitnessWriteFlag = 2;
  private final Map<BranchAccessKey, Byte> branches;
  private final Map<ChunkAccessKey, Byte> chunks;

  public AccessWitness() {
    this(new HashMap<>(), new HashMap<>());
  }

  public AccessWitness(
      final Map<BranchAccessKey, Byte> branches, final Map<ChunkAccessKey, Byte> chunks) {
    this.branches = branches;
    this.chunks = chunks;
  }

  @Override
  public void merge(final org.hyperledger.besu.datatypes.AccessWitness other) {
    // TODO VERKLE
    //    for (BranchAccessKey k : other.getBranches.keySet()) {
    //      this.branches.put(k, (byte) (this.branches.get(k) | other.getBranches.get(k)));
    //    }
    //    for (Map.Entry<ChunkAccessKey, Byte> entry : other.getChunks.entrySet()) {
    //      this.chunks.put(entry.getKey(), (byte) (this.chunks.get(entry.getKey()) |
    // entry.getValue()));
    //    }
  }

  @Override
  public List<Address> keys() {
    return this.chunks.keySet().stream()
        .map(chunkAccessKey -> chunkAccessKey.branchAccessKey().address())
        .toList();
  }

  @Override
  public AccessWitness copy() {
    AccessWitness naw = new AccessWitness();
    naw.merge(this);
    return naw;
  }

  @Override
  public long touchAndChargeProofOfAbsence(final Address address) {
    long gas = 0;
    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(0));
    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(1));
    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(2));
    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(3));
    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(4));
    return gas;
  }

  @Override
  public long touchAndChargeMessageCall(final Address address) {

    long gas = 0;

    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(0));
    gas += touchAddressOnReadAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(4));

    return gas;
  }

  @Override
  public long touchAndChargeValueTransfer(final Address caller, final Address target) {

    long gas = 0;

    gas += touchAddressOnWriteAndComputeGas(caller, zeroTreeIndex, UInt256.valueOf(1));
    gas += touchAddressOnWriteAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(1));

    return gas;
  }

  @Override
  public long touchAndChargeContractCreateInit(
      final Address address, final boolean createSendsValue) {

    long gas = 0;

    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(0));
    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(1));
    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(3));

    if (createSendsValue) {
      gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(2));
    }

    return gas;
  }

  @Override
  public long touchAndChargeContractCreateCompleted(final Address address) {

    long gas = 0;

    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(0));
    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(1));
    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(2));
    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(3));
    gas += touchAddressOnWriteAndComputeGas(address, zeroTreeIndex, UInt256.valueOf(4));

    return gas;
  }

  @SuppressWarnings("unused")
  @Override
  public long touchTxOriginAndComputeGas(final Address origin) {

    long gas = 0;

    gas += touchAddressOnReadAndComputeGas(origin, zeroTreeIndex, UInt256.valueOf(0));
    gas += touchAddressOnWriteAndComputeGas(origin, zeroTreeIndex, UInt256.valueOf(1));
    gas += touchAddressOnWriteAndComputeGas(origin, zeroTreeIndex, UInt256.valueOf(2));
    gas += touchAddressOnReadAndComputeGas(origin, zeroTreeIndex, UInt256.valueOf(3));
    gas += touchAddressOnReadAndComputeGas(origin, zeroTreeIndex, UInt256.valueOf(4));
    // modifying this after update on EIP-4762 to not charge simple transfers

    return 0;
  }

  @SuppressWarnings("unused")
  @Override
  public long touchTxExistingAndComputeGas(final Address target, final boolean sendsValue) {

    long gas = 0;

    gas += touchAddressOnReadAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(0));
    gas += touchAddressOnReadAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(2));
    gas += touchAddressOnReadAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(4));
    gas += touchAddressOnReadAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(3));

    if (sendsValue) {
      gas += touchAddressOnWriteAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(1));
    } else {
      gas += touchAddressOnReadAndComputeGas(target, zeroTreeIndex, UInt256.valueOf(1));
    }
    // modifying this after update on EIP-4762 to not charge simple transfers

    return 0;
  }

  @Override
  public long touchCodeChunksUponContractCreation(final Address address, final long codeLength) {
    long gas = 0;
    System.out.println("run touchCodeChunksUponContractCreation ? "+codeLength);
    for (long i = 0; i < (codeLength + 30) / 31; i++) {
      System.out.println("run touchCodeChunksUponContractCreation ? "+i);
      gas +=
          touchAddressOnWriteAndComputeGas(
              address,
              CODE_OFFSET.subtract(i).divide(VERKLE_NODE_WIDTH),
              CODE_OFFSET.subtract(i).mod(VERKLE_NODE_WIDTH));
    }
    return gas;
  }

  @Override
  public long touchAddressOnWriteAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {

    return touchAddressAndChargeGas(address, treeIndex, subIndex, true);
  }

  @Override
  public long touchAddressOnReadAndComputeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex) {
    return touchAddressAndChargeGas(address, treeIndex, subIndex, false);
  }

  public long touchAddressAndChargeGas(
      final Address address, final UInt256 treeIndex, final UInt256 subIndex, final boolean isWrite) {
    AccessEvents accessEvent = touchAddress(address, treeIndex, subIndex, isWrite);

    long gas = 0;
    if (accessEvent.isBranchRead()) {
      gas += WITNESS_BRANCH_READ_COST;
      System.out.println(
          "touchAddressAndChargeGas WitnessBranchReadCost "
              + address
              + " "
              + treeIndex
              + " "
              + subIndex
              + " "
              + isWrite
              + " "
              + gas);
    }
    if (accessEvent.isChunkRead()) {
      gas += WITNESS_CHUNK_READ_COST;
      System.out.println(
          "touchAddressAndChargeGas WitnessChunkReadCost "
              + address
              + " "
              + treeIndex
              + " "
              + subIndex
              + " "
              + isWrite
              + " "
              + gas);
    }
    if (accessEvent.isBranchWrite()) {
      gas += WITNESS_BRANCH_WRITE_COST;
      System.out.println(
          "touchAddressAndChargeGas WitnessBranchWriteCost "
              + address
              + " "
              + treeIndex
              + " "
              + subIndex
              + " "
              + isWrite
              + " "
              + gas);
    }
    if (accessEvent.isChunkWrite()) {
      gas += WITNESS_CHUNK_WRITE_COST;
      System.out.println(
          "touchAddressAndChargeGas WitnessChunkWriteCost "
              + address
              + " "
              + treeIndex
              + " "
              + subIndex
              + " "
              + isWrite
              + " "
              + gas);
    }
    if (accessEvent.isChunkFill()) {
      gas += WITNESS_CHUNK_FILL_COST;
      System.out.println(
          "touchAddressAndChargeGas WitnessChunkFillCost "
              + address
              + " "
              + treeIndex
              + " "
              + subIndex
              + " "
              + isWrite
              + " "
              + gas);
    }

    return gas;
  }

  public AccessEvents touchAddress(
      final Address addr, final UInt256 treeIndex, final UInt256 subIndex, final boolean isWrite) {
    AccessEvents accessEvents = new AccessEvents();
    BranchAccessKey branchKey = new BranchAccessKey(addr, treeIndex);

    ChunkAccessKey chunkKey = new ChunkAccessKey(addr, treeIndex, subIndex);

    // Read access.
    if (!this.branches.containsKey(branchKey)) {
      accessEvents.setBranchRead(true);
      this.branches.put(branchKey, AccessWitnessReadFlag);
    }
    if (!this.chunks.containsKey(chunkKey)) {
      accessEvents.setChunkRead(true);
      this.chunks.put(chunkKey, AccessWitnessReadFlag);
    }

    // TODO VERKLE: for now testnet doesn't charge
    //  chunk filling costs if the leaf was previously empty in the state
    //    boolean chunkFill = false;

    if (isWrite) {

      if ((this.branches.get(branchKey) & AccessWitnessWriteFlag) == 0) {
        accessEvents.setBranchWrite(true);
        this.branches.put(
            branchKey, (byte) (this.branches.get(branchKey) | AccessWitnessWriteFlag));
      }

      byte chunkValue = this.chunks.get(chunkKey);

      if ((chunkValue & AccessWitnessWriteFlag) == 0) {
        accessEvents.setChunkWrite(true);
        this.chunks.put(chunkKey, (byte) (this.chunks.get(chunkKey) | AccessWitnessWriteFlag));
      }
    }

    return accessEvents;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AccessWitness that = (AccessWitness) o;
    return Objects.equals(branches, that.branches) && Objects.equals(chunks, that.chunks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(branches, chunks);
  }

  @Override
  public String toString() {
    return "AccessWitness{" + "branches=" + branches + ", chunks=" + chunks + '}';
  }

  public Map<BranchAccessKey, Byte> getBranches() {
    return branches;
  }

  public Map<ChunkAccessKey, Byte> getChunks() {
    return chunks;
  }

  public record BranchAccessKey(Address address, UInt256 treeIndex) {}
  ;

  public record ChunkAccessKey(BranchAccessKey branchAccessKey, UInt256 chunkIndex) {
    public ChunkAccessKey(final Address address, final UInt256 treeIndex, final UInt256 chunkIndex) {
      this(new BranchAccessKey(address, treeIndex), chunkIndex);
    }
  }

  @Override
  public List<UInt256> getStorageSlotTreeIndexes(final UInt256 storageKey) {

    UInt256 pos;
    if (storageKey.lessThan(CODE_OFFSET.subtract(HEADER_STORAGE_OFFSET))) {
      pos = HEADER_STORAGE_OFFSET.add(storageKey);
    } else {
      pos = MAIN_STORAGE_OFFSET.add(storageKey);
    }
    return List.of(pos.divide(VERKLE_NODE_WIDTH), pos.mod(VERKLE_NODE_WIDTH));
  }
}
