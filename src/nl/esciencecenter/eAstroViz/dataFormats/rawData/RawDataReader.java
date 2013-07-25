package nl.esciencecenter.eAstroViz.dataFormats.rawData;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class RawDataReader {
    static final int NR_POLARIZATIONS = 2;

    class TransportHeader {
        int versionAndHeaderLength;
        int typeOfService;
        int totalLength;
        int identification;
        long flagsAndFragmentOffset;
        long ttl;
        long protocol;
        long headerCrc;
        long sourceIP;
        long destinationIP;
        long udpSourcePort;
        long udpDestPort;
        long udpLength;
        long udpChecksum;

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
    }

    final int clockSpeed = 200000000; // 200 MHz

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