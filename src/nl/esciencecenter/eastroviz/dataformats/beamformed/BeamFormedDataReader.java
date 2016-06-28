package nl.esciencecenter.eastroviz.dataformats.beamformed;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.esciencecenter.eastroviz.Viz;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class BeamFormedDataReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeamFormedDataReader.class);
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

    public BeamFormedMetaData readMetaData() throws IOException {
        NetcdfFile ncfile = null;
        ncfile = NetcdfFile.open(hdf5FileName);

        Group rootGroup = ncfile.getRootGroup();
        
        LOGGER.info("group = " + rootGroup.getNameAndAttributes());

        if(!rootGroup.findAttribute("FILETYPE").getStringValue().equals("bf")) {
            throw new RuntimeException("file type is not bf!");
        }
        
        m.nrStations = rootGroup.findAttribute("OBSERVATION_NOF_STATIONS").getNumericValue().intValue();
        LOGGER.info("nrStations: " + m.nrStations);

        m.totalIntegrationTime = rootGroup.findAttribute("TOTAL_INTEGRATION_TIME").getNumericValue().doubleValue();
        LOGGER.info("total integration time = " + m.totalIntegrationTime);

        m.bitsPerSample = rootGroup.findAttribute("OBSERVATION_NOF_BITS_PER_SAMPLE").getNumericValue().intValue();
        LOGGER.info("bits per sample = " + m.bitsPerSample);

        m.clockFrequency = rootGroup.findAttribute("CLOCK_FREQUENCY").getNumericValue().doubleValue();
        LOGGER.info("clock frequency = " + m.clockFrequency + " MHz");

        m.minFrequency = rootGroup.findAttribute("OBSERVATION_FREQUENCY_MIN").getNumericValue().doubleValue();
        LOGGER.info("min frequency = " + m.minFrequency);

        m.maxFrequency = rootGroup.findAttribute("OBSERVATION_FREQUENCY_MAX").getNumericValue().doubleValue();
        LOGGER.info("max frequency = " + m.maxFrequency);

        // first member is log, 2nd member is pointing 0
        Group pointing0 = rootGroup.getGroups().get(1);
        LOGGER.info("pointing0 = " + pointing0);
        LOGGER.info("attributes: " + pointing0.getAttributes());
        
        m.nrBeams = pointing0.findAttribute("NOF_BEAMS").getNumericValue().intValue();
        LOGGER.info("nrBeams = " + m.nrBeams);

        // first member of the pointing is beam 0
        LOGGER.info( "pointing0 groups: " + pointing0.getGroups());

        Group beam0 = pointing0.getGroups().get(1);
        LOGGER.info( "beam0 attributes: " + beam0.getAttributes());

        m.totalNrSamples = beam0.findAttribute("NOF_SAMPLES").getNumericValue().intValue();
        LOGGER.info("totalNrSamples = " + m.totalNrSamples);

        m.nrChannels = beam0.findAttribute("CHANNELS_PER_SUBBAND").getNumericValue().intValue();
        LOGGER.info("nrChannels = " + m.nrChannels);

        m.nrStokes = beam0.findAttribute("NOF_STOKES").getNumericValue().intValue();
        LOGGER.info("nrStokes = " + m.nrStokes);
        if (m.nrStokes != 1) {
            throw new UnsupportedOperationException("only 1 stoke supported for now.");
        }

        m.subbandWidth = beam0.findAttribute("SUBBAND_WIDTH").getNumericValue().doubleValue() / 1000000.0;
        m.channelWidth = beam0.findAttribute("CHANNEL_WIDTH").getNumericValue().doubleValue() / 1000000.0;
        m.beamCenterFrequency = beam0.findAttribute("BEAM_FREQUENCY_CENTER").getNumericValue().doubleValue();
        LOGGER.info("subbandWidth = " + m.subbandWidth + ", channelWidth = " + m.channelWidth + ", beam center frequency = "
                + m.beamCenterFrequency);

        final String stokesComponents = beam0.findAttribute("STOKES_COMPONENTS").getStringValue();
        LOGGER.info("Stokes components = Stokes" + stokesComponents);
        if (!stokesComponents.equals("I")) {
            throw new UnsupportedOperationException("only stokes I supported for now.");
        }

        // Third member of the beam is the first stoke. First is "COORDINATES", 2nd = PROCESS_HISTORY
        LOGGER.info("beam0 groups: " + beam0.getGroups());
        LOGGER.info("beam0 vars: " + beam0.getVariables());

        Variable v = beam0.getVariables().get(0);
        m.nrSubbands = v.findAttribute("NOF_SUBBANDS").getNumericValue().intValue();
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

        ncfile.close();
        
        return m;
    }

    public BeamFormedData read() throws IOException {
        return read(null, null);
    }

    public BeamFormedData read(BeamFormedMetaData m, BeamFormedSampleHandler handler) throws IOException {
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
            }

        return new BeamFormedData(fileName, maxSequenceNr, maxSubbands, zoomFactor, samples, initialFlagged, m);
    }
}
