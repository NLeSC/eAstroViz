package nl.esciencecenter.eastroviz.dataformats.raw;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class RawDataReader {
    public static final int NR_POLARIZATIONS = 2;

    class TransportHeader {
        private int versionAndHeaderLength;
        private int typeOfService;
        private int totalLength;
        private int identification;
        private long flagsAndFragmentOffset;
        private long ttl;
        private long protocol;
        private long headerCrc;
        private long sourceIP;
        private long destinationIP;
        private long udpSourcePort;
        private long udpDestPort;
        private long udpLength;
        private long udpChecksum;

        void read(final DataInputStream din) throws IOException {
            versionAndHeaderLength = din.readUnsignedByte();
            typeOfService = din.readUnsignedByte();
            totalLength = din.readUnsignedShort();
            identification = din.readUnsignedShort();
            flagsAndFragmentOffset = din.readUnsignedByte();
            ttl = din.readUnsignedByte();
            protocol = din.readUnsignedByte();
            headerCrc = din.readUnsignedShort();
            sourceIP = din.readInt(); // warning, not correct due to signed issue
            destinationIP = din.readInt(); // warning, not correct due to signed issue
            udpSourcePort = din.readUnsignedShort();
            udpDestPort = din.readUnsignedShort();
            udpLength = din.readUnsignedShort();
            udpChecksum = din.readUnsignedShort();
        }

        public int getVersionAndHeaderLength() {
            return versionAndHeaderLength;
        }

        public int getTypeOfService() {
            return typeOfService;
        }

        public int getTotalLength() {
            return totalLength;
        }

        public int getIdentification() {
            return identification;
        }

        public long getFlagsAndFragmentOffset() {
            return flagsAndFragmentOffset;
        }

        public long getTtl() {
            return ttl;
        }

        public long getProtocol() {
            return protocol;
        }

        public long getHeaderCrc() {
            return headerCrc;
        }

        public long getSourceIP() {
            return sourceIP;
        }

        public long getDestinationIP() {
            return destinationIP;
        }

        public long getUdpSourcePort() {
            return udpSourcePort;
        }

        public long getUdpDestPort() {
            return udpDestPort;
        }

        public long getUdpLength() {
            return udpLength;
        }

        public long getUdpChecksum() {
            return udpChecksum;
        }
    }

    public static final int CLOCK_SPEED = 200000000; // 200 MHz

    @SuppressWarnings("unused")
    private final String fileName;
    @SuppressWarnings("unused")
    private final int nrSubbands;
    private final DataInputStream din;
    private final RawDataFrame currentFrame;

    public RawDataReader(final String fileName, final int nrSubbands) throws IOException {
        this.fileName = fileName;
        this.nrSubbands = nrSubbands;
        currentFrame = new RawDataFrame(nrSubbands);

        final File f = new File(fileName);
        final FileInputStream fin = new FileInputStream(f);
        final BufferedInputStream bin = new BufferedInputStream(fin, 512);
        din = new DataInputStream(bin);
    }

    public RawDataFrame readFrame() {
        final boolean result = currentFrame.read(din);
        if (result) {
            return currentFrame;
        } else {
            return null;
        }
    }
}