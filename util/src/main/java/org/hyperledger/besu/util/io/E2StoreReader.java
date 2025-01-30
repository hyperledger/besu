package org.hyperledger.besu.util.io;

import com.google.common.primitives.Bytes;
import org.hyperledger.besu.util.e2.E2BeaconState;
import org.hyperledger.besu.util.e2.E2SignedBeaconBlock;
import org.hyperledger.besu.util.e2.E2SlotIndex;
import org.hyperledger.besu.util.e2.E2StoreReaderListener;
import org.hyperledger.besu.util.e2.E2Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.SnappyFramedInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class E2StoreReader {
    private static final Logger LOG = LoggerFactory.getLogger(E2StoreReader.class);
    private static final int TYPE_LENGTH = 2;
    private static final int LENGTH_LENGTH = 6;
    private static final int STARTING_SLOT_LENGTH = 8;
    private static final int SLOT_INDEX_LENGTH = 8;
    private static final int SLOT_INDEX_COUNT_LENGTH = 8;

    public void read(final InputStream inputStream, final E2StoreReaderListener listener) throws IOException {
        while (inputStream.available() > 0) {
            E2Type type = E2Type.getForTypeCode(inputStream.readNBytes(TYPE_LENGTH));
            int length = (int)convertLittleEndianBytesToLong(inputStream.readNBytes(LENGTH_LENGTH));
            int slot = 0;
            switch (type) {
                case VERSION -> {
                    //do nothing
                }
                case EMPTY -> {
                    inputStream.skipNBytes(length);
                }
                case SLOT_INDEX -> {
                    ByteArrayInputStream slotIndexInputStream = new ByteArrayInputStream(inputStream.readNBytes(length));
                    long startingSlot = convertLittleEndianBytesToLong(slotIndexInputStream.readNBytes(STARTING_SLOT_LENGTH));
                    List<Long> indexes = new ArrayList<>();
                    while(slotIndexInputStream.available() > SLOT_INDEX_COUNT_LENGTH) {
                        indexes.add(convertLittleEndianBytesToLong(slotIndexInputStream.readNBytes(SLOT_INDEX_LENGTH)));
                    }
                    long indexCount = convertLittleEndianBytesToLong(slotIndexInputStream.readNBytes(SLOT_INDEX_COUNT_LENGTH));
                    if(indexCount != indexes.size()){
                        LOG.warn("index count does not match number of indexes present for InputStream: {}", inputStream);
                    }
                    listener.handleSlotIndex(new E2SlotIndex(startingSlot, indexes));
                }
                case COMPRESSED_BEACON_STATE -> {
                    byte[] compressedBeaconStateArray = inputStream.readNBytes(length);
                    try (SnappyFramedInputStream decompressionStream = new SnappyFramedInputStream(new ByteArrayInputStream(compressedBeaconStateArray))) {
                        listener.handleBeaconState(new E2BeaconState(decompressionStream.readAllBytes(), slot++));
                    }
                } case COMPRESSED_SIGNED_BEACON_BLOCK -> {
                    byte[] compressedSignedBeaconBlockArray = inputStream.readNBytes(length);
                    try (SnappyFramedInputStream decompressionStream = new SnappyFramedInputStream(new ByteArrayInputStream(compressedSignedBeaconBlockArray))) {
                        listener.handleSignedBeaconSlot(new E2SignedBeaconBlock(decompressionStream.readAllBytes(), slot++));
                    }
                }
            }
        }
    }
    private long convertLittleEndianBytesToLong(final byte[] bytes) {
        int additionalBytes = Long.BYTES - bytes.length;
        return ByteBuffer.wrap(Bytes.concat(bytes, new byte[additionalBytes])).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}

