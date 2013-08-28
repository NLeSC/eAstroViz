package nl.esciencecenter.eastroviz.dataformats.beamformed;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.DataFormat;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.HObject;
import ncsa.hdf.object.h5.H5Group;
import ncsa.hdf.object.h5.H5ScalarDS;
import nl.esciencecenter.eastroviz.Viz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeamFormedDataReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeamFormedDataReader.class);
    private FileFormat fileFormat;
    private String rawFileName;
    private String hdf5FileName;

    final String fileName;
    final int maxSequenceNr;
    final int maxSubbands;
    final int zoomFactor;

    private int nrStokes;
    private int nrSubbands;
    private int nrChannels;
    private int nrStations;
    private int totalNrSamples;
    private int bitsPerSample;
    private double clockFrequency;
    private int nrBeams;
    private int nrSamplesPerTimeStep;
    private double minFrequency;
    private double maxFrequency;
    private int nrTimes;
    private double totalIntegrationTime; // in seconds
    private float maxVal = -10000000.0f;
    private float minVal = 1.0E20f;
    private double subbandWidth; // MHz
    private double channelWidth; // MHz
    private double beamCenterFrequency; // MHz

    public BeamFormedDataReader(final String fileName, final int maxSequenceNr, final int maxSubbands, final int zoomFactor) {
        this.fileName = fileName;
        this.maxSequenceNr = maxSequenceNr;
        this.maxSubbands = maxSubbands;
        this.zoomFactor = zoomFactor;

        final File[] ls = new File(fileName).listFiles(new Viz.ExtFilter("h5"));
        if (ls.length != 1) {
            throw new RuntimeException("more than one .h5 file");
        }
        hdf5FileName = ls[0].getPath();

        final File[] ls2 = new File(fileName).listFiles(new Viz.ExtFilter("raw"));
        if (ls2.length != 1) {
            throw new RuntimeException("more than one raw file");
        }
        rawFileName = ls2[0].getPath();

        LOGGER.info("hdf5 file = " + hdf5FileName + ", raw file = " + rawFileName);
    }

    private void readMetaData() {
        // retrieve an instance of H5File
        final FileFormat fileFormat1 = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);
        if (fileFormat1 == null) {
            LOGGER.info("Cannot find HDF5 FileFormat.");
            System.exit(1);
        }

        try {
            fileFormat = fileFormat1.createInstance(hdf5FileName, FileFormat.READ);
            if (fileFormat == null) {
                LOGGER.info("Cannot open HDF5 File.");
                System.exit(1);
            }

            // open the file and retrieve the file structure
            @SuppressWarnings("unused")
            final int fileID = fileFormat.open();
        } catch (final Exception e) {
            LOGGER.info("" + e);
        }

        final H5Group root = (H5Group) ((javax.swing.tree.DefaultMutableTreeNode) fileFormat.getRootNode()).getUserObject();
        LOGGER.info("root object: " + root + " type is: " + root.getClass() + " full name = " + root.getFullName());
        final java.util.List<HObject> rootMemberList = root.getMemberList();
        for (int i = 0; i < rootMemberList.size(); i++) {
            LOGGER.info("root member " + i + " = " + rootMemberList.get(i));
        }
        printAttributes(root);

        // Sanity check data type
        if (!getAttribute(root, "FILETYPE").getPrimitiveStringVal().equals("bf")) {
            throw new RuntimeException("file type is not bf!");
        }

        nrStations = getAttribute(root, "OBSERVATION_NOF_STATIONS").getPrimitiveIntVal();
        LOGGER.info("nrStations = " + nrStations);

        totalIntegrationTime = getAttribute(root, "TOTAL_INTEGRATION_TIME").getPrimitiveDoubleVal();
        LOGGER.info("total integration time = " + totalIntegrationTime);

        bitsPerSample = getAttribute(root, "OBSERVATION_NOF_BITS_PER_SAMPLE").getPrimitiveIntVal();
        LOGGER.info("bits per sample = " + bitsPerSample);

        clockFrequency = getAttribute(root, "CLOCK_FREQUENCY").getPrimitiveDoubleVal();
        LOGGER.info("clock frequency = " + clockFrequency + " MHz");

        minFrequency = getAttribute(root, "OBSERVATION_FREQUENCY_MIN").getPrimitiveDoubleVal();
        LOGGER.info("min frequency = " + minFrequency);

        maxFrequency = getAttribute(root, "OBSERVATION_FREQUENCY_MAX").getPrimitiveDoubleVal();
        LOGGER.info("max frequency = " + maxFrequency);

        // first member is pointing 0
        final H5Group pointing0 = (H5Group) rootMemberList.get(0);
        LOGGER.info("-- start pointing 0 --");
        printAttributes(pointing0);
        LOGGER.info("-- end pointing 0 --");

        nrBeams = getAttribute(pointing0, "NOF_BEAMS").getPrimitiveIntVal();
        LOGGER.info("nrBeams = " + nrBeams);

        // first member of the pointing is beam 0
        final java.util.List<HObject> pointing0MemberList = pointing0.getMemberList();
        for (int i = 0; i < pointing0MemberList.size(); i++) {
            LOGGER.info("pointing 0 member " + i + " = " + pointing0MemberList.get(i));
        }

        final H5Group beam0 = (H5Group) pointing0MemberList.get(0);
        printAttributes(beam0);

        final java.util.List<HObject> beam0MemberList = beam0.getMemberList();
        for (int i = 0; i < beam0MemberList.size(); i++) {
            LOGGER.info("beam 0 member " + i + " = " + beam0MemberList.get(i));
        }

        totalNrSamples = getAttribute(beam0, "NOF_SAMPLES").getPrimitiveIntVal();
        LOGGER.info("totalNrSamples = " + totalNrSamples);

        nrChannels = getAttribute(beam0, "CHANNELS_PER_SUBBAND").getPrimitiveIntVal();
        LOGGER.info("nrChannels = " + nrChannels);

        nrStokes = getAttribute(beam0, "NOF_STOKES").getPrimitiveIntVal();
        LOGGER.info("nrStokes = " + nrStokes);
        if (nrStokes != 1) {
            throw new UnsupportedOperationException("only 1 stoke supported for now.");
        }

        subbandWidth = getAttribute(beam0, "SUBBAND_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        channelWidth = getAttribute(beam0, "CHANNEL_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        beamCenterFrequency = getAttribute(beam0, "BEAM_FREQUENCY_CENTER").getPrimitiveDoubleVal();
        LOGGER.info("subbandWidth = " + subbandWidth + ", channelWidth = " + channelWidth + ", beam center frequency = "
                + beamCenterFrequency);

        final String stokesComponents = getAttribute(beam0, "STOKES_COMPONENTS").getPrimitiveStringVal();
        LOGGER.info("Stokes components = " + stokesComponents);
        if (!stokesComponents.equals("I")) {
            throw new UnsupportedOperationException("only stokes I supported for now.");
        }

        // Third member of the beam is the first stoke. First is "COORDINATES", 2nd = PROCESS_HISTORY
        final H5ScalarDS stokes0 = (H5ScalarDS) beam0MemberList.get(2);

        System.err.println("stokes0 = " + stokes0);

        printAttributes(stokes0);

        final int[] nrChannelsArray = getAttribute(stokes0, "NOF_CHANNELS").get1DIntArrayVal();
        nrSubbands = nrChannelsArray.length;
        LOGGER.info("nrSubbands = " + nrSubbands);

        nrSamplesPerTimeStep = (int) (totalNrSamples / (totalIntegrationTime * zoomFactor));

        nrTimes = (int) (totalIntegrationTime * zoomFactor);
        if (maxSequenceNr < nrTimes) {
            nrTimes = maxSequenceNr;
        }
        LOGGER.info("nrSeconds = " + nrTimes + ", nrSamplesPerTimeStep = " + nrSamplesPerTimeStep);
    }

    public BeamFormedData read() {
        readMetaData();

        float[][][] samples = new float[nrTimes][nrSubbands][nrChannels];
        boolean[][][] initialFlagged = new boolean[nrTimes][nrSubbands][nrChannels];
        int second = 0;

        final ByteBuffer bb = ByteBuffer.allocateDirect(nrSamplesPerTimeStep * nrSubbands * nrChannels * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        final FloatBuffer fb = bb.asFloatBuffer();

        FileInputStream fin = null;

        try {
            final File f = new File(rawFileName);
            fin = new FileInputStream(f);

            final FileChannel ch = fin.getChannel();

            // boost::extents[nrSamples | 2][nrSubbands][nrChannels] 
            // the | 2 extra samples are not written to disk, only kept in memory!
            for (second = 0; second < nrTimes; second++) {
                if (second > maxSequenceNr) {
                    break;
                }
                final double size = (nrSamplesPerTimeStep * nrSubbands * nrChannels * 4) / (1024.0 * 1024.0);
                LOGGER.debug("reading second " + second + ", size = " + size + " MB");
                final long start = System.currentTimeMillis();

                bb.rewind();
                fb.rewind();
                final int res = ch.read(bb);
                if (res < 0) {
                    nrTimes = second;
                    break;
                }

                for (int sample = 0; sample < nrSamplesPerTimeStep; sample++) {
                    for (int subband = 0; subband < nrSubbands; subband++) {
                        for (int channel = 0; channel < nrChannels; channel++) {
                            final float val = fb.get();
                            if (val <= 0.0f) {
                                // we integrate; if one sample in the integration time was flagged, flag everything.
                                initialFlagged[second][subband][channel] = true;
                            }
                            samples[second][subband][channel] += val;
                            //                            logger.info("sample at time " + sample + ", subband " + subband + ", channel " + channel + " = " + val);

                        }
                    }
                }
                final long end = System.currentTimeMillis();
                final double time = (end - start) / 1000.0;
                final double speed = size / time;
                LOGGER.debug("read rook " + time + " s, speed = " + speed + " MB/s");
            }
        } catch (final IOException e) {
            nrTimes = second; // oops, we read less data...
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                    fin = null;
                } catch (final IOException e) {
                    // ignore
                }
            }

            try {
                fileFormat.close();
                fileFormat = null;
            } catch (final Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        return new BeamFormedData(fileName, maxSequenceNr, maxSubbands, samples, initialFlagged, nrStokes, nrSubbands,
                nrChannels, nrStations, totalNrSamples, bitsPerSample, clockFrequency, nrBeams, nrSamplesPerTimeStep,
                minFrequency, maxFrequency, nrTimes, totalIntegrationTime, maxVal, minVal, subbandWidth, channelWidth,
                beamCenterFrequency, zoomFactor);
    }

    @SuppressWarnings("unchecked")
    private Hdf5Attribute getAttribute(final DataFormat node, final String name) {
        List<Attribute> l = null;

        try {
            l = node.getMetadata();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        for (int i = 0; i < l.size(); i++) {
            final Attribute a = l.get(i);
            if (a.getName().equals(name)) {
                return new Hdf5Attribute(a);
            }
        }
        return null;
    }

    private void printAttributes(final DataFormat node) {
        try {
            @SuppressWarnings("unchecked")
            final List<Attribute> metaList = node.getMetadata();
            for (int i = 0; i < metaList.size(); i++) {
                final Attribute a = metaList.get(i);
                final Hdf5Attribute a2 = new Hdf5Attribute(a);
                LOGGER.info("node: " + node.toString() + ": meta " + i + " = " + a.getName() + ", type = "
                        + a.getType().getDatatypeDescription() + ", nrDims = " + a.getRank() + ", size = "
                        + a.getType().getDatatypeSize() + ", val = " + a2.getValueString());
            }
        } catch (final Exception e1) {
            e1.printStackTrace();
        }
    }
}
