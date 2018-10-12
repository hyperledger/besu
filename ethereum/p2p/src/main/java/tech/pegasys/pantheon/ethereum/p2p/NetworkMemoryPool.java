package tech.pegasys.pantheon.ethereum.p2p;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;

public class NetworkMemoryPool {

  private static final ByteBufAllocator ALLOCATOR = new PooledByteBufAllocator();

  public static ByteBuf allocate(final int size) {
    return ALLOCATOR.ioBuffer(0, size);
  }
}
