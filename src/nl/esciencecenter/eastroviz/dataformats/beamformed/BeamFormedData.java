/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import nl.esciencecenter.eastroviz.AntennaBandpass;
import nl.esciencecenter.eastroviz.AntennaType;
import nl.esciencecenter.eastroviz.Viz;
import nl.esciencecenter.eastroviz.dataformats.DataProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BeamFormedData extends DataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeamFormedData.class);

    public static final boolean REMOVE_CHANNEL_0_FROM_VIEW = true;

    public static final boolean COLLAPSE_DEDISPERSED_DATA = false;
    public static final int NR_PERIODS_IN_FOLD = 1;
    public static final boolean CORRECT_ANTENNA_BANDPASS = false;

    private float[][][] data; // [second][subband][channel]
    private boolean[][][] flagged; // [second][subband][channel]
    private boolean[][][] initialFlagged; // [second][subband][channel]

    private int nrStokes;
    private int nrChannels;
    private int nrSubbands;
    private int nrStations;
    private int totalNrSamples;
    private double totalIntegrationTime;
    private int bitsPerSample;
    private double clockFrequency;
    private int nrBeams;

    private double minFrequency;
    private double maxFrequency;

    private int nrSamplesPerTimeStep;
    private int nrTimes;

    private float maxVal = -10000000.0f;
    private float minVal = 1.0E20f;

    private float subbandWidth; // MHz
    private float channelWidth; // MHz
    private float beamCenterFrequency; // MHz

    private String rawFileName;
    private String hdf5FileName;
    private FileFormat fileFormat;

    private final int zoomFactor;
    private static int maximumShift;

    private int stoke = 0;

    public BeamFormedData(final String fileName, final int maxSequenceNr, final int maxSubbands, final int zoomFactor) {
        super();
        init(fileName, maxSequenceNr, maxSubbands, new String[] { "I" }, new String[] { "none" });

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

        this.zoomFactor = zoomFactor;
    }

    public void read() {
        readMetaData();

        nrSamplesPerTimeStep = (int) (totalNrSamples / (totalIntegrationTime * zoomFactor));
        nrTimes = (int) (totalIntegrationTime * zoomFactor);
        if (getMaxSequenceNr() < nrTimes) {
            nrTimes = getMaxSequenceNr();
        }

        LOGGER.info("nrSeconds = " + nrTimes + ", nrSamplesPerSecond = " + nrSamplesPerTimeStep);

        data = new float[nrTimes][nrSubbands][nrChannels];
        initialFlagged = new boolean[nrTimes][nrSubbands][nrChannels];
        flagged = new boolean[nrTimes][nrSubbands][nrChannels];
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
                if (second > getMaxSequenceNr()) {
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
                            data[second][subband][channel] += val;
                            //                            logger.info("sample at time " + sample + ", subband " + subband + ", channel " + channel + " = " + val);

                        }
                    }
                }
                final long end = System.currentTimeMillis();
                final double time = (end - start) / 1000.0;
                final double speed = size / time;
                LOGGER.debug("read rook " + time + " s, speed = " + speed + " MB/s, min = " + minVal + ", max = " + maxVal);
            }
        } catch (final IOException e) {
            nrTimes = second; // oops, we read less data...
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

            LOGGER.info("START correction");
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
                    float frequency = getStartFrequency(subband, channel);
                    float correctionFactor = bandPass.getBandPassCorrectionFactor(AntennaType.HBA_LOW, frequency);
                    LOGGER.info(frequency + " " + correctionFactor);
                }
            }
            LOGGER.info("END correction");

            for (int s = 0; s < nrTimes; s++) {
                for (int subband = 0; subband < nrSubbands; subband++) {
                    for (int channel = 0; channel < nrChannels; channel++) {
                        float frequency = getStartFrequency(subband, channel);
                        float correctionFactor = bandPass.getBandPassCorrectionFactor(AntennaType.HBA_LOW, frequency);
                        //                        logger.info("freq of subband " + subband + ", channel " + channel +  " = " + frequency + ", correctionFactor = " + correctionFactor);
                        data[s][subband][channel] *= correctionFactor;
                    }
                }
            }
        }

        calculateStatistics();
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
        LOGGER.info("nrSamples = " + totalNrSamples);

        nrChannels = getAttribute(beam0, "CHANNELS_PER_SUBBAND").getPrimitiveIntVal();
        LOGGER.info("nrChannels = " + nrChannels);

        nrStokes = getAttribute(beam0, "NOF_STOKES").getPrimitiveIntVal();
        LOGGER.info("nrStokes = " + nrStokes);
        if (nrStokes != 1) {
            LOGGER.info("only 1 stoke supported for now.");
            System.exit(1);
        }

        subbandWidth = (float) getAttribute(beam0, "SUBBAND_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        channelWidth = (float) getAttribute(beam0, "CHANNEL_WIDTH").getPrimitiveDoubleVal() / 1000000.0f;
        beamCenterFrequency = (float) getAttribute(beam0, "BEAM_FREQUENCY_CENTER").getPrimitiveDoubleVal();

        LOGGER.info("subbandWidth = " + subbandWidth + ", channelWidth = " + channelWidth + ", beam center frequency = " + beamCenterFrequency);

        final String stokesComponents = getAttribute(beam0, "STOKES_COMPONENTS").getPrimitiveStringVal();
        LOGGER.info("Stokes components = " + stokesComponents);
        if (!stokesComponents.equals("I")) {
            LOGGER.info("only stokes I supported for now.");
            System.exit(1);
        }

        // Third member of the beam is the first stoke. First is "COORDINATES", 2nd = PROCESS_HISTORY
        final H5ScalarDS stokes0 = (H5ScalarDS) beam0MemberList.get(2);

        System.err.println("stokes0 = " + stokes0);

        printAttributes(stokes0);

        final int[] nrChannelsArray = getAttribute(stokes0, "NOF_CHANNELS").get1DIntArrayVal();
        nrSubbands = nrChannelsArray.length;
        LOGGER.info("nrSubbands = " + nrSubbands);
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
                LOGGER.info("node: " + node.toString() + ": meta " + i + " = " + a.getName() + ", type = " + a.getType().getDatatypeDescription()
                        + ", nrDims = " + a.getRank() + ", size = " + a.getType().getDatatypeSize() + ", val = " + a2.getValueString());
            }
        } catch (final Exception e1) {
            e1.printStackTrace();
        }
    }

    private void calculateStatistics() {
        long initialFlaggedCount = 0;

        // calc min and max for scaling
        for (int second = 0; second < nrTimes; second++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
                    if (initialFlagged[second][subband][channel]) {
                        initialFlaggedCount++;
                    } else {
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
        
        long nrSamples = nrTimes * nrSubbands *nrChannels;
        float percent = ((float) initialFlaggedCount / nrSamples) * 100.0f;

        LOGGER.info("samples already flagged in data set: " + initialFlaggedCount + "(" + percent + "%)");
    }

    public void close() {
        try {
            fileFormat.close();
        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void dedisperse(float dm) {
        dedisperse(data, flagged, zoomFactor, minFrequency, channelWidth, dm);
        calculateStatistics();
    }

    public float[] fold(float period) {
        return fold(data, flagged, zoomFactor, period);
    }

    public static int[] computeShifts(int nrSubbands, int nrChannels, float nrSamplesPerSecond, double lowFreq, double freqStep, float dm) {
        int nrFrequencies = nrSubbands * nrChannels;

        int[] shifts = new int[nrFrequencies];

        double highFreq = (lowFreq + ((nrFrequencies - 1) * freqStep));
        double inverseHighFreq = 1.0f / (highFreq * highFreq);

        double kDM = 4148.808f * dm;

        for (int freq = 0; freq < nrFrequencies; freq++) {
            double inverseFreq = 1.0f / ((lowFreq + (freq * freqStep)) * (lowFreq + (freq * freqStep)));
            double delta = kDM * (inverseFreq - inverseHighFreq);
            shifts[freq] = (int) (delta * nrSamplesPerSecond);
            //            logger.info("inverseFreq = " + inverseFreq + ", delta = " + delta + ", shift = " + shifts[subband]);
        }
        return shifts;
    }

    public static void dedisperse(float[][][] data, boolean[][][] flagged, float nrSamplesPerSecond, double lowFreq, double freqStep, float dm) {
        int nrTimes = data.length;
        int nrSubbands = data[0].length;
        int nrChannels = data[0][0].length;
        int nrFrequencies = nrSubbands * nrChannels;

        int[] shifts = computeShifts(nrSubbands, nrChannels, nrSamplesPerSecond, lowFreq, freqStep, dm);
        maximumShift = 0;
        for (int shift : shifts) {
            if (shift > maximumShift) {
                maximumShift = shift;
            }
        }

        for (int i = 0; i < shifts.length; i++) {
            LOGGER.info("shift " + i + " = " + shifts[i]);
        }

        for (int time = 0; time < nrTimes; time++) {
            for (int freq = 0; freq < nrFrequencies; freq++) {
                int subband = freq / nrChannels;
                int channel = freq % nrChannels;

                int posX = time + shifts[freq];
                if (posX < nrTimes) {
                    data[time][subband][channel] = data[posX][subband][channel];
                    flagged[time][subband][channel] = flagged[posX][subband][channel];
                } else {
                    data[time][subband][channel] = 0.0f;
                    flagged[time][subband][channel] = true;
                }
            }
        }

        if (COLLAPSE_DEDISPERSED_DATA) {
            // collapse in subband 0, channel 0
            for (int time = 0; time < nrTimes; time++) {
                int count = 0;
                for (int subband = 1; subband < nrSubbands; subband++) {
                    for (int channel = 0; channel < nrChannels; channel++) {
                        if (!flagged[time][subband][channel]) {
                            data[time][0][0] += data[time][subband][channel];
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    data[time][0][0] /= count;
                }
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("collapsed data:");
                for (int time = maximumShift; time < nrTimes - maximumShift; time++) {
                    LOGGER.trace("" + data[time][0][0]);
                }
                LOGGER.trace("end of collapsed data");
            }

            // and also copy it to all other subbands and channels, so we can see it better
            for (int time = 0; time < nrTimes; time++) {
                for (int subband = 1; subband < nrSubbands; subband++) {
                    for (int channel = 0; channel < nrChannels; channel++) {
                        data[time][subband][channel] = data[time][0][0];
                    }
                }
            }
        }
    }

    public static float[] fold(float[][][] data, boolean[][][] flagged, float nrSamplesPerSecond, float period) {
        int nrTimes = data.length;
        int nrSubbands = data[0].length;
        int nrChannels = data[0][0].length;
        if (COLLAPSE_DEDISPERSED_DATA) {
            nrSubbands = 1;
        }

        float nrSamplesToFold = nrSamplesPerSecond * period * NR_PERIODS_IN_FOLD;
        float[] res = new float[(int) Math.ceil(nrSamplesToFold)];
        int[] count = new int[res.length];

        LOGGER.info("nrTimes = " + nrTimes + ", nrSubbands = " + nrSubbands + ", nrSamplesPerSecond = " + nrSamplesPerSecond + ", length of folded output = "
                + res.length);

        for (int time = maximumShift; time < nrTimes - maximumShift; time++) {
            int mod = Math.round(time % nrSamplesToFold);
            if (mod >= res.length) {
                mod = res.length - 1;
            }
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
                    if (!flagged[time][subband][channel]) {
                        res[mod] += data[time][subband][channel];
                        count[mod]++;
                    }
                }
            }
        }

        for (int i = 0; i < res.length; i++) {
            if (count[i] > 0) {
                res[i] /= count[i];
            }
        }
        DataProvider.scale(res);

        float snr = computeSNR(res);
        LOGGER.info("signal to noise ratio is: " + snr);

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

    public float getStartFrequency(int subband, int channel) {
        float startFreq = beamCenterFrequency - (float) Math.floor(nrSubbands / 2.0) * subbandWidth;
        return startFreq + subband * subbandWidth + channel * channelWidth;
    }

    public int getNrStokes() {
        return nrStokes;
    }

    @Override
    public int getNrSubbands() {
        return nrSubbands;
    }

    public int getNrSamples() {
        return totalNrSamples;
    }

    public int getNrSamplesPerTimeStep() {
        return nrSamplesPerTimeStep;
    }

    @Override
    public int getSizeX() {
        if (getMaxSequenceNr() > 0 && getMaxSequenceNr() < nrTimes) {
            return getMaxSequenceNr();
        }
        return nrTimes;
    }

    @Override
    public int getSizeY() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return nrSubbands * (nrChannels - 1);
        } else {
            return nrSubbands * nrChannels;
        }
    }

    @Override
    public int getNrChannels() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return nrChannels - 1;
        } else {
            return 1;
        }
    }

    private int getSubbandIndex(int frequency) {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return frequency / (nrChannels - 1);
        } else {
            return frequency / nrChannels;
        }
    }

    private int getChannelIndex(int frequency) {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return frequency % (nrChannels - 1) + 1;
        } else {
            return frequency % nrChannels;
        }
    }

    @Override
    public float getValue(final int x, final int y) {
        return (getRawValue(x, y) - minVal) / (maxVal - minVal);
    }

    @Override
    public float getRawValue(final int x, final int y) {
        return data[x][getSubbandIndex(y)][getChannelIndex(y)];
    }

    @Override
    public boolean isFlagged(final int x, final int y) {
        return initialFlagged[x][getSubbandIndex(y)][getChannelIndex(y)];
    }

    public float[][][] getData() {
        return data;
    }

    @Override
    public void flag() {
        // We do not have a beam formed data flagger at the moment.
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

    @Override
    public int getStation1() {
        return -1;
    }

    @Override
    public int setStation1(int station1) {
        return -1;
    }

    @Override
    public int getStation2() {
        return -1;
    }

    @Override
    public int setStation2(int station2) {
        return -1;
    }

    @Override
    public int getPolarization() {
        return stoke;
    }

    @Override
    public int setPolarization(int newValue) {
        return stoke;
    }

    @Override
    public String polarizationToString(int pol) {
        if (pol == 0) {
            return "I";
        } else {
            LOGGER.warn("illegal polarization");
            return "illegal polarization";
        }
    }

    @Override
    public int StringToPolarization(String polString) {
        if (polString.equals("I")) {
            return 0;
        } else {
            LOGGER.warn("illegal polarization");
            return -1;
        }
    }
}
