package nl.esciencecenter.eastroviz.dataformats.visibility;

import java.io.IOException;

import nl.esciencecenter.eastroviz.Viz;
import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.flaggers.PostCorrelationFlagger;
import nl.esciencecenter.eastroviz.flaggers.PostCorrelationHistorySmoothedSumThresholdFlagger;
import nl.esciencecenter.eastroviz.flaggers.PostCorrelationHistorySumThresholdFlagger;
import nl.esciencecenter.eastroviz.flaggers.PostCorrelationSmoothedSumThresholdFlagger;
import nl.esciencecenter.eastroviz.flaggers.PostCorrelationSumThresholdFlagger;
import nl.esciencecenter.eastroviz.flaggers.PostCorrelationThresholdFlagger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents all time samples and frequencies of a given baseline.
 */
public final class VisibilityData extends DataProvider {
    static final boolean PRINT_STATS = false;
    static final boolean REMOVE_CHANNEL_0_FROM_VIEW = true;

    private static final Logger logger = LoggerFactory.getLogger(VisibilityData.class);

    private final MSReader r;
    private final float[][][][] powers; // [time][nrSubbands][nrChannels][nrCrossPolarizations]
    private final int[][][] nrValidSamples; // [time][nrSubbands][nrChannels]
    private final boolean[][][] flagged; // [time][nrSubbands][nrChannels]
    private int baseline;
    private int station1;
    private int station2;
    private int pol;
    private final int nrChannels;
    private final int integrationTime;
    private final int nrStations;
    private final int nrBaselines;
    private final int nrSubbands;
    private final int nrCrossPolarizations;
    private int nrSeconds;

    public VisibilityData(final String fileName, final int station1, final int station2, final int pol, final int maxSequenceNr, final int maxSubbands) throws IOException {
        super();
        init(fileName, maxSequenceNr, maxSubbands, new String[] { "XX", "XY", "YX", "YY" }, new String[] { "none", "Threshold", "SumThreshold",
                "SmoothedSumThreshold", "HistorySumThreshold", "HistorySmoothedSumThreshold" });
        this.station1 = station1;
        this.station2 = station2;
        this.baseline = baseline(station1, station2);
        this.pol = pol;

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

    public static int baseline(final int station1, final int station2) {
        assert(station1 <= station2);
        return station2 * (station2 + 1) / 2 + station1;
    }

    // TODO
    /*
     *  from c++ code:
inline void Correlator::baselineToStations(const unsigned baseline, unsigned& station1, unsigned& station2)
{
  station2 = (unsigned) (sqrtf(2 * baseline + .25f) - .5f);
  station1 = baseline - station2 * (station2 + 1) / 2;
}

inline bool Correlator::baselineIsAutoCorrelation(const unsigned baseline)
{
  unsigned station1, station2;
  baselineToStations(baseline, station1, station2);
  return station1 == station2;
}

     */
    
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

        logger.info("Reading data for stations (" + station1 + ", " + station2 + "), baseline " + baseline + ", subband " + subband + "...");
        long start = System.currentTimeMillis();

        long sequenceNr = 0;
        int timeIndex = 0;
        do {
            sequenceNr = readSecond(subband, timeIndex);
            timeIndex++;
        } while (sequenceNr >= 0 && sequenceNr < getMaxSequenceNr());
        try {
            r.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
        nrSeconds = timeIndex;
        logger.info("Read " + nrSeconds + " time samples of data. Read took " + ((end - start) / 1000.0) + " seconds.");
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

        if (getFlaggerType() == null || getFlaggerType().equals("none")) {
            return;
        }

        final long start = System.currentTimeMillis();

        final PostCorrelationFlagger[] flaggers = new PostCorrelationFlagger[nrSubbands];

        for (int i = 0; i < nrSubbands; i++) {
            if (getFlaggerType().equals(getFlaggerList()[1])) {
                flaggers[i] = new PostCorrelationThresholdFlagger(nrChannels, getFlaggerSensitivity(), getFlaggerSIRValue());
            } else if (getFlaggerType().equals(getFlaggerList()[2])) {
                flaggers[i] = new PostCorrelationSumThresholdFlagger(nrChannels, getFlaggerSensitivity(), getFlaggerSIRValue());
            } else if (getFlaggerType().equals(getFlaggerList()[3])) {
                flaggers[i] = new PostCorrelationSmoothedSumThresholdFlagger(nrChannels, getFlaggerSensitivity(), getFlaggerSIRValue());
            } else if (getFlaggerType().equals(getFlaggerList()[4])) {
                flaggers[i] = new PostCorrelationHistorySumThresholdFlagger(nrChannels, getFlaggerSensitivity(), getFlaggerSIRValue());
            } else if (getFlaggerType().equals(getFlaggerList()[5])) {
                flaggers[i] = new PostCorrelationHistorySmoothedSumThresholdFlagger(nrChannels, getFlaggerSensitivity(), getFlaggerSIRValue());
            } else {
                throw new RuntimeException("illegal flagger selected: " + getFlaggerType());
            }
        }
        logger.info("Selected " + getFlaggerType().getClass().getName());

        for (int time = 0; time < nrSeconds; time++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                flaggers[subband].flag(powers[time][subband], nrValidSamples[time][subband], flagged[time][subband]);
            }
        }

        final long end = System.currentTimeMillis();
        logger.info("Flagging with " + getFlaggerType() + ", sensitivity " + getFlaggerSensitivity() + " took " + (end - start) + " ms.");
    }

    public int getNrFrequencies() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return nrSubbands * (nrChannels - 1);
        } else {
            return nrSubbands * nrChannels;
        }
    }

    public float getPower(final int time, final int frequency) {
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
    public float getValue(final int x, final int y) { // TODO SCALE
        return getPower(x, y);
    }

    @Override
    public float getRawValue(final int x, final int y) {
        return getPower(x, y);
    }

    public int getStation1() {
        return station1;
    }

    public int getStation2() {
        return station2;
    }

    @Override
    public int setStation1(int station1) {
        if(station1 < 0 || station1 >= nrStations || station1 == this.station1) {
            return this.station1;
        }
        
        this.station1 = station1;
        this.baseline = baseline(station1, station2);
        try {
            read();
        } catch (IOException e) {
            logger.error("" + e);
            throw new RuntimeException(e); 
        }
        return this.station1;
    }

    @Override
    public int setStation2(int station2) {
        if(station2 < 0 || station2 >= nrStations || station2 == this.station2) {
            return this.station2;
        }
        
        this.station2 = station2;
        this.baseline = baseline(station1, station2);
        try {
            read();
        } catch (IOException e) {
            logger.error("" + e);
            throw new RuntimeException(e); 
        }
        return this.station2;
    }

    public String polarizationToString(final int pol) {
        switch (pol) {
        case 0:
            return "XX";
        case 1:
            return "XY";
        case 2:
            return "YX";
        case 3:
            return "YY";
        default:
            return "error";
        }
    }

    public int StringToPolarization(final String polString) {
        if (polString.equals("XX")) {
            return 0;
        } else if (polString.equals("XY")) {
            return 1;
        } else if (polString.equals("YX")) {
            return 2;
        } else if (polString.equals("YY")) {
            return 3;
        } else {
            logger.warn("illegal polarization: " + polString);
            return 0;
        }
    }

    @Override
    public int getPolarization() {
        return pol; 
    }

    @Override
    public int setPolarization(int newValue) {
        if(pol < 0 || pol >= getPolList().length) {
            return pol;
        }
        this.pol = newValue;
        return newValue;
    }
}
