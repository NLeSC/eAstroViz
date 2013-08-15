package nl.esciencecenter.eastroviz.dataformats.raw;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RawDataFrameHeader {
    public static final int FRAME_HEADER_SIZE = 16; // bytes

    private static final Logger logger = LoggerFactory.getLogger(RawDataFrameHeader.class);

    private int versionId;
    private int sourceInfo;
    private int configurationId;
    private int stationId;
    private int nofBeamlets;
    private int nofBlocks;
    private long timestamp;
    private long blockSequenceNumber;

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
        logger.debug("versionId = " + versionId);
        logger.debug("sourceInfo = " + sourceInfo);
        logger.debug("configurationId = " + configurationId);
        logger.debug("stationId = " + stationId);
        logger.debug("nofBeamlets = " + nofBeamlets);
        logger.debug("nofBlocks = " + nofBlocks);
        logger.debug("timestamp = " + timestamp);
        logger.debug("blockSequenceNumber = " + blockSequenceNumber);
    }

    public int getVersionId() {
        return versionId;
    }

    public int getSourceInfo() {
        return sourceInfo;
    }

    public int getConfigurationId() {
        return configurationId;
    }

    public int getStationId() {
        return stationId;
    }

    public int getNofBeamlets() {
        return nofBeamlets;
    }

    public int getNofBlocks() {
        return nofBlocks;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getBlockSequenceNumber() {
        return blockSequenceNumber;
    }
}
