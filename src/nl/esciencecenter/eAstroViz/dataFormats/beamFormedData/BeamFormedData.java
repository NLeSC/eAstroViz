package nl.esciencecenter.eAstroViz.dataFormats.beamFormedData;

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
import nl.esciencecenter.eAstroViz.AntennaBandpass;
import nl.esciencecenter.eAstroViz.AntennaType;
import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BeamFormedData extends DataProvider {
    private static final Logger logger = LoggerFactory.getLogger(BeamFormedData.class);

    static final boolean COLLAPSE_DEDISPERSED_DATA = false;
    static final int NR_PERIODS_IN_FOLD = 1;
    static final boolean CORRECT_ANTENNA_BANDPASS = false;

    float[][][] data; // [second][subband][channel]
    boolean[][][] flagged;

    int nrStokes;
    int nrChannels;
    int nrSamples;
    int nrSubbands;
    int nrStations;
    double totalIntegrationTime;
    int bitsPerSample;
    double clockFrequency;
    int nrBeams;

    int nrSamplesPerSecond;
    int nrSeconds;

    float maxVal = -10000000.0f;
    float minVal = 1.0E20f;

    float subbandWidth; // MHz
    float channelWidth; //MHz
    float beamCenterFrequency; // MHz

    String rawFileName;
    String hdf5FileName;
    FileFormat fileFormat;

    final int zoomFactor;
    static int maximumShift;

    public BeamFormedData(final String fileName, final int maxSequenceNr, final int maxSubbands, final int zoomFactor) {
        super(fileName, maxSequenceNr, maxSubbands, new String[] { "I" }, new String[] { "none" });

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

        logger.info("hdf5 file = " + hdf5FileName + ", raw file = " + rawFileName);

        this.zoomFactor = zoomFactor;
    }

    public void read() {
        readMetaData();

        totalIntegrationTime *= zoomFactor;
        nrSamplesPerSecond = (int) (nrSamples / totalIntegrationTime);
        nrSeconds = (int) totalIntegrationTime;
        if (maxSequenceNr < nrSeconds) {
            nrSeconds = maxSequenceNr;
        }

        logger.info("nrSeconds = " + nrSeconds + ", nrSamplesPerSecond = " + nrSamplesPerSecond);

        data = new float[nrSeconds][nrSubbands][nrChannels];
        flagged = new boolean[nrSeconds][nrSubbands][nrChannels];
        int second = 0;

        final ByteBuffer bb = ByteBuffer.allocateDirect(nrSamplesPerSecond * nrSubbands * nrChannels * 4);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        final FloatBuffer fb = bb.asFloatBuffer();

        FileInputStream fin = null;

        try {
            final File f = new File(rawFileName);
            fin = new FileInputStream(f);

            final FileChannel ch = fin.getChannel();

            // boost::extents[nrSamples | 2][nrSubbands][nrChannels] // the | 2
            // extra samples are not written to disk, only kept in memory!
            for (second = 0; second < nrSeconds; second++) {
                if (second > maxSequenceNr) {
                    break;
                }
                final double size = (nrSamplesPerSecond * nrSubbands * nrChannels * 4) / (1024.0 * 1024.0);
                logger.info("reading second " + second + ", size = " + size + " MB");
                final long start = System.currentTimeMillis();

                bb.rewind();
                fb.rewind();
                final int res = ch.read(bb); // TODO can return less bytes!
                if (res < 0) {
                    nrSeconds = second;
                    break;
                }

                for (int sample = 0; sample < nrSamplesPerSecond; sample++) {
                    for (int subband = 0; subband < nrSubbands; subband++) {
                        for (int channel = 0; channel < nrChannels; channel++) {
                            final float val = fb.get();
                            data[second][subband][channel] += val;
                            //                            logger.info("sample at time " + sample + ", subband " + subband + ", channel " + channel + " = " + val);

                        }
                    }
                }
                final long end = System.currentTimeMillis();
                final double time = (end - start) / 1000.0;
                final double speed = size / time;
                logger.info(", read rook " + time + " s, speed = " + speed + " MB/s, min = " + minVal + ", max = " + maxVal);
            }
        } catch (final Exception e) {
            nrSeconds = second; // oops, we read less data...
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (final IOException e) {
                    // ignore
                }
            }
        }

        if (CORRECT_ANTENNA_BANDPASS) {
            AntennaBandpass bandPass = new AntennaBandpass();

            logger.info("START correction");
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
                    float frequency = getFrequency(subband, channel);
                    float correctionFactor = bandPass.getBandPassCorrectionFactor(AntennaType.HBA_LOW, frequency);
                    logger.info(frequency + " " + correctionFactor);
                }
            }
            logger.info("END correction");

            for (int s = 0; s < nrSeconds; s++) {
                for (int subband = 0; subband < nrSubbands; subband++) {
                    for (int channel = 0; channel < nrChannels; channel++) {
                        float frequency = getFrequency(subband, channel);
                        float correctionFactor = bandPass.getBandPassCorrectionFactor(AntennaType.HBA_LOW, frequency);
                        //                        logger.info("freq of subband " + subband + ", channel " + channel +  " = " + frequency + ", correctionFactor = " + correctionFactor);
                        data[s][subband][channel] *= correctionFactor;
                    }
                }
            }
        }

        calculateStatistics();
        /*
                logger.info("start of beamFormed data");
                for (second = 0; second < nrSeconds; second++) {
                     logger.info(data[second][29][0]);
                }
                logger.info("end of beamFormed data");
        */
    }

    public float getFrequency(int subband, int channel) {
        float startFreq = beamCenterFrequency - (nrSubbands / 2) * subbandWidth;
        return startFreq + subband * subbandWidth + channel * channelWidth;
    }

    private void calculateStatistics() {
        // calc min and max for scaling
        for (int second = 0; second < nrSeconds; second++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
                    if (data[second][subband][channel] < minVal) {
                        minVal = data[second][subband][channel];
                    }
                    if (data[second][subband][channel] > maxVal) {
                        maxVal = data[second][subband][channel];
                    }
                }
            }
        }
    }

    public void readMetaData() {
        // retrieve an instance of H5File
        final FileFormat fileFormat1 = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);
        if (fileFormat1 == null) {
            logger.info("Cannot find HDF5 FileFormat.");
            System.exit(1);
        }

        try {
            fileFormat = fileFormat1.createInstance(hdf5FileName, FileFormat.READ);
            if (fileFormat == null) {
                logger.info("Cannot open HDF5 File.");
                System.exit(1);
            }

            // open the file and retrieve the file structure
            @SuppressWarnings("unused")
            final int fileID = fileFormat.open();
        } catch (final Exception e) {
            logger.info("" + e);
        }

        final H5Group root = (H5Group) ((javax.swing.tree.DefaultMutableTreeNode) fileFormat.getRootNode()).getUserObject();
        logger.info("root object: " + root + " type is: " + root.getClass() + " full name = " + root.getFullName());
        final java.util.List<HObject> l = root.getMemberList();
        for (int i = 0; i < l.size(); i++) {
            logger.info("member " + i + " = " + l.get(i));
        }
        printAttributes(root);

        // Sanity check data type
        if (!getAttribute(root, "FILETYPE").getPrimitiveStringVal().equals("bf")) {
            throw new RuntimeException("file type is not bf!");
        }

        nrStations = getAttribute(root, "OBSERVATION_NOF_STATIONS").getPrimitiveIntVal();
        logger.info("nrStations = " + nrStations);

        totalIntegrationTime = getAttribute(root, "TOTAL_INTEGRATION_TIME").getPrimitiveDoubleVal();
        logger.info("total integration time = " + totalIntegrationTime);

        bitsPerSample = getAttribute(root, "OBSERVATION_NOF_BITS_PER_SAMPLE").getPrimitiveIntVal();
        logger.info("bits per sample = " + bitsPerSample);

        clockFrequency = getAttribute(root, "CLOCK_FREQUENCY").getPrimitiveDoubleVal();
        logger.info("clock frequency = " + clockFrequency + " MHz");

        // first member is pointing 0
        final H5Group pointing0 = (H5Group) l.get(0);
        logger.info("-----");
        printAttributes(pointing0);
        logger.info("-----");

        nrBeams = getAttribute(pointing0, "NOF_BEAMS").getPrimitiveIntVal();
        logger.info("nrBeams = " + nrBeams);

        // first member of the pointing is beam 0
        final java.util.List<HObject> l2 = pointing0.getMemberList();
        final H5Group beam0 = (H5Group) l2.get(0);
        printAttributes(beam0);

        nrSamples = getAttribute(beam0, "NOF_SAMPLES").getPrimitiveIntVal();
        logger.info("nrSamples = " + nrSamples);

        nrChannels = getAttribute(beam0, "CHANNELS_PER_SUBBAND").getPrimitiveIntVal();
        logger.info("nrChannels = " + nrChannels);

        nrStokes = getAttribute(beam0, "NOF_STOKES").getPrimitiveIntVal();
        logger.info("nrStokes = " + nrStokes);
        if (nrStokes != 1) {
            logger.info("only 1 stoke supported for now.");
            System.exit(1);
        }

        subbandWidth = (float) getAttribute(beam0, "SUBBAND_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        channelWidth = (float) getAttribute(beam0, "CHANNEL_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        beamCenterFrequency = (float) getAttribute(beam0, "BEAM_FREQUENCY_CENTER").getPrimitiveDoubleVal();

        final String stokesComponents = getAttribute(beam0, "STOKES_COMPONENTS").getPrimitiveStringVal();
        logger.info("Stokes components = " + stokesComponents);
        if (!stokesComponents.equals("I")) {
            logger.info("only stokesI supported for now.");
            System.exit(1);
        }

        // Third member of the beam is the first stoke. First is "COORDINATES", 2nd = PROCESS_HISTORY
        final java.util.List<HObject> l3 = beam0.getMemberList();
        final H5ScalarDS stoke0 = (H5ScalarDS) l3.get(2);
        printAttributes(stoke0);

        final int[] nrChannelsArray = getAttribute(stoke0, "NOF_CHANNELS").get1DIntArrayVal();
        nrSubbands = nrChannelsArray.length;
        logger.info("nrSubbands = " + nrSubbands);
    }

    public void close() {
        try {
            fileFormat.close();
        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    public Hdf5Attribute getAttribute(final DataFormat node, final String name) {
        List<Attribute> l = null;

        try {
            l = node.getMetadata();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        for (int i = 0; i < l.size(); i++) {
            final Attribute a = l.get(i);
            if (a.getName().equals(name)) {
                return new Hdf5Attribute(a);
            }
        }
        return null;
    }

    void printAttributes(final DataFormat node) {
        try {
            @SuppressWarnings("unchecked")
            final List<Attribute> metaList = node.getMetadata();
            for (int i = 0; i < metaList.size(); i++) {
                final Attribute a = metaList.get(i);
                final Hdf5Attribute a2 = new Hdf5Attribute(a);
                logger.info("meta " + i + " = " + a.getName() + ", type = " + a.getType().getDatatypeDescription() + ", nrDims = " + a.getRank() + ", size = "
                        + a.getType().getDatatypeSize() + ", val = " + a2.getValueString());
            }
        } catch (final Exception e1) {
            e1.printStackTrace();
        }
    }

    public int getTotalTime() {
        if (maxSequenceNr > 0 && maxSequenceNr < nrSeconds) {
            return maxSequenceNr;
        }
        return nrSeconds;
    }

    public int getTotalFreq() {
        return nrSubbands * nrChannels;
    }

    public int getNrStokes() {
        return nrStokes;
    }

    public int getNrChannels() {
        return nrChannels;
    }

    public int getNrSubbands() {
        return nrSubbands;
    }

    public int getNrSamples() {
        return nrSamples;
    }

    public int getNrSamplesPerSecond() {
        return nrSamplesPerSecond;
    }

    @Override
    public int getSizeX() {
        return getTotalTime();
    }

    @Override
    public int getSizeY() {
        return getTotalFreq();
    }

    @Override
    public float getValue(final int x, final int y, int stoke) {
        return (getRawValue(x, y, stoke) - minVal) / (maxVal - minVal);
    }

    @Override
    public float getRawValue(final int x, final int y, int stoke) { // TODO: maybe return other stokes
        final int subband = y / nrChannels;
        final int channel = y % nrChannels;
        return data[x][subband][channel];
    }

    @Override
    public boolean isFlagged(final int x, final int y) {
        return false; // not stored in file format yet.
    }

    public float[][][] getData() {
        return data;
    }

    @Override
    public void flag() {
        // TODO Auto-generated method stub
    }

    public void dedisperse(float lowFreq, float freqStep, float dm) {
        dedisperse(data, flagged, zoomFactor, lowFreq, freqStep, dm);
        calculateStatistics();
    }

    public float[] fold(float period) {
        return fold(data, flagged, zoomFactor, period);
    }

    public static int[] computeShifts(int nrSubbands, float nrSamplesPerSecond, float lowFreq, float freqStep, float dm) {
        int[] shifts = new int[nrSubbands];

        float highFreq = (lowFreq + ((nrSubbands - 1) * freqStep));
        float inverseHighFreq = 1.0f / (highFreq * highFreq);

        float kDM = 4148.808f * dm;

        for (int subband = 0; subband < nrSubbands; subband++) {
            float inverseFreq = 1.0f / ((lowFreq + (subband * freqStep)) * (lowFreq + (subband * freqStep)));
            float delta = kDM * (inverseFreq - inverseHighFreq);
            shifts[subband] = (int) (delta * nrSamplesPerSecond);
            //            logger.info("inverseFreq = " + inverseFreq + ", delta = " + delta + ", shift = " + shifts[subband]);
        }
        return shifts;
    }

    public static void dedisperse(float[][][] data, boolean[][][] flagged, float nrSamplesPerSecond, float lowFreq, float freqStep, float dm) {
        int nrTimes = data.length;
        int nrSubbands = data[0].length;

        int[] shifts = computeShifts(nrSubbands, nrSamplesPerSecond, lowFreq, freqStep, dm);
        maximumShift = 0;
        for (int shift : shifts) {
            if (shift > maximumShift) {
                maximumShift = shift;
            }
        }

        for (int i = 0; i < shifts.length; i++) {
            logger.info("shift " + i + " = " + shifts[i]);
        }

        for (int time = 0; time < nrTimes; time++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                int posX = time + shifts[subband];
                if (posX < nrTimes) {
                    data[time][subband][0] = data[posX][subband][0];
                    flagged[time][subband][0] = flagged[posX][subband][0];
                } else {
                    data[time][subband][0] = 0.0f;
                    flagged[time][subband][0] = true;
                }
            }
        }

        if (COLLAPSE_DEDISPERSED_DATA) {
            // collapse in subband 0
            for (int time = 0; time < nrTimes; time++) {
                int count = 0;
                for (int subband = 1; subband < nrSubbands; subband++) {
                    if (!flagged[time][subband][0]) {
                        data[time][0][0] += data[time][subband][0];
                        count++;
                    }
                }
                if (count > 0) {
                    data[time][0][0] /= count;
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace("collapsed data:");
                for (int time = maximumShift; time < nrTimes - maximumShift; time++) {
                    logger.trace("" + data[time][0][0]);
                }
                logger.trace("end of collapsed data");
            }

            // and also copy it to all other subbands, so we can see it better
            for (int time = 0; time < nrTimes; time++) {
                for (int subband = 1; subband < nrSubbands; subband++) {
                    data[time][subband][0] = data[time][0][0];
                }
            }
        }
    }

    public static float[] fold(float[][][] data, boolean[][][] flagged, float nrSamplesPerSecond, float period) {
        int nrTimes = data.length;
        int nrSubbands = data[0].length;
        if (COLLAPSE_DEDISPERSED_DATA) {
            nrSubbands = 1;
        }

        float nrSamplesToFold = nrSamplesPerSecond * period * NR_PERIODS_IN_FOLD;
        float[] res = new float[(int) Math.ceil(nrSamplesToFold)];
        int[] count = new int[res.length];

        logger.info("nrTimes = " + nrTimes + ", nrSubbands = " + nrSubbands + ", nrSamplesPerSecond = " + nrSamplesPerSecond + ", length of folded output = "
                + res.length);

        for (int time = maximumShift; time < nrTimes - maximumShift; time++) {
            int mod = Math.round(time % nrSamplesToFold);
            if (mod >= res.length) {
                mod = res.length - 1;
            }
            for (int subband = 0; subband < nrSubbands; subband++) {
                if (!flagged[time][subband][0]) {
                    res[mod] += data[time][subband][0];
                    count[mod]++;
                }
            }
        }

        for (int i = 0; i < res.length; i++) {
            if (count[i] > 0) {
                res[i] /= count[i];
            }
        }
        DataProvider.scale(res);

        for (float re : res) {
            System.out.println(re);
        }

        float snr = computeSNR(res);
        logger.info("signal to noise ratio is: " + snr);

        return res;
    }

    public static float computeSNR(float[] res) {
        // SNR = (max – mean) / RMS
        float max = -1;
        float mean = 0;
        float rms = 0;
        for (float re : res) {
            if (re > max) {
                max = re;
            }
            mean += re;
            rms += re * re;
        }
        mean /= res.length;
        rms /= res.length;
        rms = (float) Math.sqrt(rms);

        return (max - mean) / rms;
    }

    public float getSubbandWidth() {
        return subbandWidth;
    }

    public float getChannelWidth() {
        return channelWidth;
    }

    public float getBeamCenterFrequency() {
        return beamCenterFrequency;
    }
}
