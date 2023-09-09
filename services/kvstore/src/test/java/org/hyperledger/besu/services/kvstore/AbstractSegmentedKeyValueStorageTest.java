package org.hyperledger.besu.services.kvstore;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.kvstore.AbstractKeyValueStorageTest;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage.SEGMENT_IDENTIFIER;

public abstract class AbstractSegmentedKeyValueStorageTest extends AbstractKeyValueStorageTest {
  public abstract SegmentedKeyValueStorage createSegmentedStore();

  @Test
  public void assertSegmentedIsNearestTo() throws Exception {
    try (final var store = this.createSegmentedStore()) {

      // create 10 entries
      final SegmentedKeyValueStorageTransaction tx = store.startTransaction();
      IntStream.range(0, 10).forEach(i -> {
        final byte[] key = bytesFromHexString("000" + i);
        final byte[] value = bytesFromHexString("0FFF");
        tx.put(SEGMENT_IDENTIFIER, key, value);
        // different common prefix, and reversed order of bytes:
        final byte[] key2 = bytesFromHexString("010" + (9 - i));
        final byte[] value2 = bytesFromHexString("0FFF");
        tx.put(SEGMENT_IDENTIFIER, key2, value2);
      });
      tx.commit();

      // assert 0009 is closest to 000F
      var val =
          store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("000F"));
      assertThat(val).isPresent();
      assertThat(val.get().key()).isEqualTo(Bytes.fromHexString("0009"));

      // assert 0109 is closest to 010D
      var val2 =
          store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("010D"));
      assertThat(val2).isPresent();
      assertThat(val2.get().key()).isEqualTo(Bytes.fromHexString("0109"));

      // assert 0103 is closest to 0103
      var val3 =
          store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0103"));
      assertThat(val3).isPresent();
      assertThat(val3.get().key()).isEqualTo(Bytes.fromHexString("0103"));

      // assert 0003 is closest to 0003
      var val4 =
          store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0003"));
      assertThat(val4).isPresent();
      assertThat(val4.get().key()).isEqualTo(Bytes.fromHexString("0003"));

      // assert 0000 is closest to 0000
      var val5 =
          store.getNearestTo(SEGMENT_IDENTIFIER, Bytes.fromHexString("0000"));
      assertThat(val5).isPresent();
      assertThat(val5.get().key()).isEqualTo(Bytes.fromHexString("0000"));
    }
  }
}
