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

    BeamFormedMetaData m = new BeamFormedMetaData();

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

    public BeamFormedMetaData readMetaData() {
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

        m.nrStations = getAttribute(root, "OBSERVATION_NOF_STATIONS").getPrimitiveIntVal();
        LOGGER.info("nrStations = " + m.nrStations);

        m.totalIntegrationTime = getAttribute(root, "TOTAL_INTEGRATION_TIME").getPrimitiveDoubleVal();
        LOGGER.info("total integration time = " + m.totalIntegrationTime);

        m.bitsPerSample = getAttribute(root, "OBSERVATION_NOF_BITS_PER_SAMPLE").getPrimitiveIntVal();
        LOGGER.info("bits per sample = " + m.bitsPerSample);

        m.clockFrequency = getAttribute(root, "CLOCK_FREQUENCY").getPrimitiveDoubleVal();
        LOGGER.info("clock frequency = " + m.clockFrequency + " MHz");

        m.minFrequency = getAttribute(root, "OBSERVATION_FREQUENCY_MIN").getPrimitiveDoubleVal();
        LOGGER.info("min frequency = " + m.minFrequency);

        m.maxFrequency = getAttribute(root, "OBSERVATION_FREQUENCY_MAX").getPrimitiveDoubleVal();
        LOGGER.info("max frequency = " + m.maxFrequency);

        // first member is pointing 0
        final H5Group pointing0 = (H5Group) rootMemberList.get(0);
        LOGGER.info("-- start pointing 0 --");
        printAttributes(pointing0);
        LOGGER.info("-- end pointing 0 --");

        m.nrBeams = getAttribute(pointing0, "NOF_BEAMS").getPrimitiveIntVal();
        LOGGER.info("nrBeams = " + m.nrBeams);

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

        m.totalNrSamples = getAttribute(beam0, "NOF_SAMPLES").getPrimitiveIntVal();
        LOGGER.info("totalNrSamples = " + m.totalNrSamples);

        m.nrChannels = getAttribute(beam0, "CHANNELS_PER_SUBBAND").getPrimitiveIntVal();
        LOGGER.info("nrChannels = " + m.nrChannels);

        m.nrStokes = getAttribute(beam0, "NOF_STOKES").getPrimitiveIntVal();
        LOGGER.info("nrStokes = " + m.nrStokes);
        if (m.nrStokes != 1) {
            throw new UnsupportedOperationException("only 1 stoke supported for now.");
        }

        m.subbandWidth = getAttribute(beam0, "SUBBAND_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        m.channelWidth = getAttribute(beam0, "CHANNEL_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        m.beamCenterFrequency = getAttribute(beam0, "BEAM_FREQUENCY_CENTER").getPrimitiveDoubleVal();
        LOGGER.info("subbandWidth = " + m.subbandWidth + ", channelWidth = " + m.channelWidth + ", beam center frequency = "
                + m.beamCenterFrequency);

        final String stokesComponents = getAttribute(beam0, "STOKES_COMPONENTS").getPrimitiveStringVal();
        LOGGER.info("Stokes components = " + stokesComponents);
        if (!stokesComponents.equals("I")) {
            throw new UnsupportedOperationException("only stokes I supported for now.");
        }

        // Third member of the beam is the first stoke. First is "COORDINATES", 2nd = PROCESS_HISTORY
        final H5ScalarDS stokes0 = (H5ScalarDS) beam0MemberList.get(2);
        printAttributes(stokes0);

        final int[] nrChannelsArray = getAttribute(stokes0, "NOF_CHANNELS").get1DIntArrayVal();
        m.nrSubbands = nrChannelsArray.length;
        LOGGER.info("nrSubbands = " + m.nrSubbands);


        if (zoomFactor >= 1) {
            m.nrTimes = (int) (m.totalIntegrationTime * zoomFactor);
            m.nrSamplesPerTimeStep = (int) (m.totalNrSamples / (m.totalIntegrationTime * zoomFactor));
        } else {
            // full resolution
            m.nrTimes = m.totalNrSamples;
            m.nrSamplesPerTimeStep = (int) (m.totalNrSamples / m.totalIntegrationTime);
        }
        if (maxSequenceNr < m.nrTimes) {
            m.nrTimes = maxSequenceNr;
        }
        LOGGER.info("nrSeconds = " + m.nrTimes + ", nrSamplesPerTimeStep = " + m.nrSamplesPerTimeStep);

        return m;
    }

    public BeamFormedData read() {
        return read(null, null);
    }

    public BeamFormedData read(BeamFormedMetaData m, BeamFormedSampleHandler handler) {
        if (m == null) {
            m = readMetaData();
        }

        float[][][] samples = null;
        boolean[][][] initialFlagged = null;

        if (handler == null) {
            samples = new float[m.nrTimes][m.nrSubbands][m.nrChannels];
            initialFlagged = new boolean[m.nrTimes][m.nrSubbands][m.nrChannels];
        }

        int second = 0;

        final ByteBuffer bb = ByteBuffer.allocateDirect(m.nrSamplesPerTimeStep * m.nrSubbands * m.nrChannels * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        final FloatBuffer fb = bb.asFloatBuffer();

        FileInputStream fin = null;

        try {
            final File f = new File(rawFileName);
            fin = new FileInputStream(f);

            final FileChannel ch = fin.getChannel();

            // boost::extents[nrSamples | 2][nrSubbands][nrChannels] 
            // the | 2 extra samples are not written to disk, only kept in memory!
            for (second = 0; second < m.nrTimes; second++) {
                if (second > maxSequenceNr) {
                    break;
                }
                final double size = (m.nrSamplesPerTimeStep * m.nrSubbands * m.nrChannels * 4) / (1024.0 * 1024.0);
                LOGGER.debug("reading second " + second + ", size = " + size + " MB");
                final long start = System.currentTimeMillis();

                bb.rewind();
                fb.rewind();
                final int res = ch.read(bb);
                if (res < 0) {
                    m.nrTimes = second;
                    break;
                }

                for (int sample = 0; sample < m.nrSamplesPerTimeStep; sample++) {
                    for (int subband = 0; subband < m.nrSubbands; subband++) {
                        for (int channel = 0; channel < m.nrChannels; channel++) {
                            final float val = fb.get();
                            if (handler != null) {
                                handler.handleSample(second, sample, subband, channel, val);
                            } else {
                                if (!initialFlagged[second][subband][channel]) {
                                    if (val <= 0.0f) {
                                        // we integrate; if one sample in the integration time was flagged, flag everything.
                                        initialFlagged[second][subband][channel] = true;
                                    } else {
                                        samples[second][subband][channel] += val;
                                    }
                                }
                            }
                        }
                    }
                }
                final long end = System.currentTimeMillis();
                final double time = (end - start) / 1000.0;
                final double speed = size / time;
                LOGGER.debug("read rook " + time + " s, speed = " + speed + " MB/s");
            }
        } catch (final IOException e) {
            m.nrTimes = second; // oops, we read less data...
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

        return new BeamFormedData(fileName, maxSequenceNr, maxSubbands, zoomFactor, samples, initialFlagged, m);
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
