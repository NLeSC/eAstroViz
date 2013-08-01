package nl.esciencecenter.eAstroViz.dataFormats.visibilityData;

import java.io.IOException;

import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;
import nl.esciencecenter.eAstroViz.flaggers.PostCorrelationFlagger;
import nl.esciencecenter.eAstroViz.flaggers.PostCorrelationHistorySmoothedSumThresholdFlagger;
import nl.esciencecenter.eAstroViz.flaggers.PostCorrelationHistorySumThresholdFlagger;
import nl.esciencecenter.eAstroViz.flaggers.PostCorrelationSmoothedSumThresholdFlagger;
import nl.esciencecenter.eAstroViz.flaggers.PostCorrelationSumThresholdFlagger;
import nl.esciencecenter.eAstroViz.flaggers.PostCorrelationThresholdFlagger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents all time samples and frequencies of a given baseline.
 */
public final class VisibilityData extends DataProvider {
    static final boolean PRINT_STATS = false;
    static final boolean REMOVE_CHANNEL_0_FROM_VIEW = true;

    private static final Logger logger = LoggerFactory.getLogger(VisibilityData.class);

    final private MSReader r;

    final float[][][][] powers; // [time][nrSubbands][nrChannels][nrCrossPolarizations]
    final int[][][] nrValidSamples; // [time][nrSubbands][nrChannels]
    final boolean[][][] flagged; // [time][nrSubbands][nrChannels]
    final int baseline;
    final int station1;
    final int station2;
    final int nrChannels;
    final int integrationTime;
    final int nrStations;
    private final int nrBaselines;
    private final int nrSubbands;
    private final int nrCrossPolarizations;
    private int nrSeconds;

    public VisibilityData(final String fileName, final int station1, final int station2, final int maxSequenceNr, final int maxSubbands) throws IOException {
        super(fileName, maxSequenceNr, maxSubbands, new String[] { "XX", "XY", "YX", "YY" }, new String[] { "none", "Threshold", "SumThreshold",
                "SmoothedSumThreshold", "HistorySumThreshold", "HistorySmoothedSumThreshold" });
        this.station1 = station1;
        this.station2 = station2;
        this.baseline = Viz.baseline(station1, station2);

        r = new MSReader(fileName);
        nrSubbands = r.getNrSubbands();

        // open subband 0, to read the metadata
        r.openSubband(0);
        this.nrChannels = r.getMetaData().getNrChannels();
        this.nrBaselines = r.getMetaData().getNrBaselines();
        this.integrationTime = r.getMetaData().getIntegrationTimeProd();
        this.nrCrossPolarizations = r.getMetaData().getNrCrossPolarizations();
        this.nrStations = r.getMetaData().getNrStations();
        this.nrSeconds = r.getMaxNrSecondsOfData();
        r.close();

        powers = new float[nrSeconds][nrSubbands][nrChannels][nrCrossPolarizations];
        nrValidSamples = new int[nrSeconds][nrSubbands][nrChannels];
        flagged = new boolean[nrSeconds][nrSubbands][nrChannels];

        if (baseline >= nrBaselines) {
            throw new IOException("illegal baseline");
        }
        
        logger.info("nrSubbands = " + nrSubbands + ", nrChannels = " + nrChannels + ", nrBaseLines = " + nrBaselines + ", integrationTime = " + integrationTime +
                ", pols = " + nrCrossPolarizations + ", nrStations = " + nrStations + ", nrSeconds = " + nrSeconds); 
    }

    public static MSMetaData getMetaData(final String fileName) {
        MSReader r = null;
        try {
            r = new MSReader(fileName);
            r.openSubband(0);
            final MSMetaData m = r.getMetaData();
            r.close();
            return m;
        } catch (final IOException e) {
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (final IOException e) {
                    // Ignore.
                }
            }
        }
    }

    public void read() throws IOException {
        for (int i = 0; i < nrSubbands; i++) {
            readSubband(i);
        }
    }

    private void readSubband(final int subband) {

        try {
            r.openSubband(subband);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        logger.debug("Reading data for stations (" + station1 + ", " + station2 + "), baseline " + baseline + ", subband " + subband + "...");
        long start = System.currentTimeMillis();

        long sequenceNr = 0;
        int timeIndex = 0;
        do {
            sequenceNr = readSecond(subband, timeIndex);
            timeIndex++;
        } while (sequenceNr >= 0 && sequenceNr < maxSequenceNr);
        try {
            r.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
        nrSeconds = timeIndex;
        logger.debug("Read " + nrSeconds + " time samples of data. Read took " + ((end - start) / 1000.0) + " seconds.");
    }

    private long readSecond(final int subband, final int timeIndex) {
        r.readSecond(baseline);
        if (r.getSequenceNr() >= 0) {
            addSecond(subband, r.getVisibilities(baseline), r.getNrValidSamples(baseline), r.getSequenceNr(), timeIndex);
        }
        return r.getSequenceNr();
    }

    private void addSecond(final int subband, final float[][][] vis, // [nrChannels][nrCrossPolarizations][2]
            final int[] nrValidSamplesIn, // [nrChannels]
            final long sequenceNr, final int timeIndex) {

        if (timeIndex >= powers.length) {
            return;
        }

        // for now, we assume there are no seconds missing...
        if (sequenceNr != timeIndex) {
            logger.warn("WARNING, DATA WAS DROPPED, seq no = " + sequenceNr + " expected = " + timeIndex);
        }

        if(logger.isTraceEnabled()) {
        for (int channel = 1; channel < nrChannels; channel++) {
            if (nrValidSamplesIn[channel] != integrationTime) {
                logger.trace("WARNING, DATA WAS FLAGGED, sequenceNr = " + sequenceNr + ", baseline = " + baseline + ", channel = " + channel
                        + ", samples is only " + nrValidSamplesIn[channel] + ", expected " + integrationTime);
            }
        }
        }
        
        for (int channel = 0; channel < nrChannels; channel++) {
            for (int pol = 0; pol < nrCrossPolarizations; pol++) {
                final float real = vis[channel][pol][Viz.REAL];
                final float imag = vis[channel][pol][Viz.IMAG];
                final float power = real * real + imag * imag;
                powers[timeIndex][subband][channel][pol] = power;
            }
            nrValidSamples[timeIndex][subband][channel] = nrValidSamplesIn[channel];
        }
    }

    @Override
    public void flag() {
        for (int time = 0; time < nrSeconds; time++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
                    flagged[time][subband][channel] = false;
                }
            }
        }

        if (flaggerType == null || flaggerType.equals("none")) {
            return;
        }

        final long start = System.currentTimeMillis();

        final PostCorrelationFlagger[] flaggers = new PostCorrelationFlagger[nrSubbands];

        for (int i = 0; i < nrSubbands; i++) {
            if (flaggerType.equals(flaggerList[1])) {
                flaggers[i] = new PostCorrelationThresholdFlagger(nrChannels, flaggerSensitivity, flaggerSIRValue);
            } else if (flaggerType.equals(flaggerList[2])) {
                flaggers[i] = new PostCorrelationSumThresholdFlagger(nrChannels, flaggerSensitivity, flaggerSIRValue);
            } else if (flaggerType.equals(flaggerList[3])) {
                flaggers[i] = new PostCorrelationSmoothedSumThresholdFlagger(nrChannels, flaggerSensitivity, flaggerSIRValue);
            } else if (flaggerType.equals(flaggerList[4])) {
                flaggers[i] = new PostCorrelationHistorySumThresholdFlagger(nrChannels, flaggerSensitivity, flaggerSIRValue);
            } else if (flaggerType.equals(flaggerList[5])) {
                flaggers[i] = new PostCorrelationHistorySmoothedSumThresholdFlagger(nrChannels, flaggerSensitivity, flaggerSIRValue);
            } else {
                throw new RuntimeException("illegal flagger selected: " + flaggerType);
            }
        }
        logger.info("Selected " + flaggerType.getClass().getName());

        for (int time = 0; time < nrSeconds; time++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                flaggers[subband].flag(powers[time][subband], nrValidSamples[time][subband], flagged[time][subband]);
            }
        }

        final long end = System.currentTimeMillis();
        logger.info("Flagging with " + flaggerType + ", sensitivity " + flaggerSensitivity + " took " + (end - start) + " ms.");
    }

    public int getNrFrequencies() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return nrSubbands * (nrChannels - 1);
        } else {
            return nrSubbands * nrChannels;
        }
    }

    public float getPower(final int time, final int frequency, final int pol) {
        int subband;
        int channel;
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            subband = frequency / (nrChannels - 1);
            channel = frequency % (nrChannels - 1) + 1;
        } else {
            subband = frequency / nrChannels;
            channel = frequency % nrChannels;
        }

        return powers[time][subband][channel][pol];
    }

    @Override
    public boolean isFlagged(final int time, final int frequency) {
        if (flagged == null) {
            return false;
        }

        int subband;
        int channel;
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            subband = frequency / (nrChannels - 1);
            channel = frequency % (nrChannels - 1) + 1;
        } else {
            subband = frequency / nrChannels;
            channel = frequency % nrChannels;
        }

        return flagged[time][subband][channel];
    }

    public int getNrValidSamples(final int time, final int frequency) {
        int subband;
        int channel;
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            subband = frequency / (nrChannels - 1);
            channel = frequency % (nrChannels - 1) + 1;
        } else {
            subband = frequency / nrChannels;
            channel = frequency % nrChannels;
        }

        return nrValidSamples[time][subband][channel];
    }

    public String getSummaryString(final int pol1) {
        return "TODO";
    }

    public int getNrSubbands() {
        return nrSubbands;
    }

    public int getNrStations() {
        return nrStations;
    }

    @Override
    public int getSizeX() {
        return nrSeconds;
    }

    @Override
    public int getSizeY() {
        return getNrFrequencies();
    }

    @Override
    public float getValue(final int x, final int y, int pol) { // TODO SCALE
        return getPower(x, y, pol);
    }

    @Override
    public float getRawValue(final int x, final int y, int pol) {
        return getPower(x, y, pol);
    }

    public int getStation1() {
        return station1;
    }

    public int getStation2() {
        return station2;
    }
}