package org.hyperledger.besu.ethereum.core;

public class AccessEvents {
  private boolean branchRead;
  private boolean chunkRead;
  private boolean branchWrite;
  private boolean chunkWrite;
  private boolean chunkFill;

  public AccessEvents() {
    this(false, false, false, false, false);
  }

  public AccessEvents(
      final boolean branchRead,
      final boolean chunkRead,
      final boolean branchWrite,
      final boolean chunkWrite,
      final boolean chunkFill) {
    this.branchRead = branchRead;
    this.chunkRead = chunkRead;
    this.branchWrite = branchWrite;
    this.chunkWrite = chunkWrite;
    this.chunkFill = chunkFill;
  }

  public boolean isBranchRead() {
    return branchRead;
  }

  public boolean isChunkRead() {
    return chunkRead;
  }

  public boolean isBranchWrite() {
    return branchWrite;
  }

  public boolean isChunkWrite() {
    return chunkWrite;
  }

  public boolean isChunkFill() {
    return chunkFill;
  }

  public void setBranchRead(final boolean branchRead) {
    this.branchRead = branchRead;
  }

  public void setChunkRead(final boolean chunkRead) {
    this.chunkRead = chunkRead;
  }

  public void setBranchWrite(final boolean branchWrite) {
    this.branchWrite = branchWrite;
  }

  public void setChunkWrite(final boolean chunkWrite) {
    this.chunkWrite = chunkWrite;
  }

  public void setChunkFill(final boolean chunkFill) {
    this.chunkFill = chunkFill;
  }
}
