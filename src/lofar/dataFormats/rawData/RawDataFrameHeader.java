package lofar.dataFormats.rawData;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

class RawDataFrameHeader {
    public static final int FRAME_HEADER_SIZE = 16; // bytes

    int versionId;
    int sourceInfo;
    int configurationId;
    int stationId;
    int nofBeamlets;
    int nofBlocks;
    long timestamp;
    long blockSequenceNumber;

    boolean read(final DataInputStream din) {
        try {
            versionId = din.readUnsignedByte();
            sourceInfo = din.readUnsignedByte();
            configurationId = din.readUnsignedShort();
            stationId = din.readUnsignedShort();
            nofBeamlets = din.readUnsignedByte();
            nofBlocks = din.readUnsignedByte();
            timestamp = readUnsignedInt(din);
            blockSequenceNumber = readUnsignedInt(din);
        } catch (final IOException e) {
            return false;
        }

        return true;
    }

    public long readUnsignedInt(final DataInputStream din) throws IOException {
        final int ch1 = din.read();
        final int ch2 = din.read();
        final int ch3 = din.read();
        final int ch4 = din.read();

        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }
        return (ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0);
    }

    void print() {
        System.out.println("versionId = " + versionId);
        System.out.println("sourceInfo = " + sourceInfo);
        System.out.println("configurationId = " + configurationId);
        System.out.println("stationId = " + stationId);
        System.out.println("nofBeamlets = " + nofBeamlets);
        System.out.println("nofBlocks = " + nofBlocks);
        System.out.println("timestamp = " + timestamp);
        System.out.println("blockSequenceNumber = " + blockSequenceNumber);
    }
}
