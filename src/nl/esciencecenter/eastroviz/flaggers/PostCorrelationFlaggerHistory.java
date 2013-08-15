package nl.esciencecenter.eastroviz.flaggers;

import java.util.ArrayList;

import nl.esciencecenter.eastroviz.Viz;

public class PostCorrelationFlaggerHistory {
    static final int HISTORY_SIZE = 16;
    static final int HISTORY_STEP_SIZE = 1;
    static final int MIN_HISTORY_SIZE = 4; // HISTORY_SIZE;

    static class HistoryElement {
        @SuppressWarnings("unused")
        private int second;
        private float mean;
        private float median;
        @SuppressWarnings("unused")
        private float stdDev;
        private float[] freqData;

        HistoryElement(final int second, final float mean, final float median, final float stdDev, final float[] freqData) {
            this.second = second;
            this.mean = mean;
            this.median = median;
            this.stdDev = stdDev;
            this.freqData = freqData.clone();
        }
    }

    // history, element 0 is the most recent
    @SuppressWarnings("unchecked")
    private ArrayList<HistoryElement> history[] = new ArrayList[Viz.NR_POLARIZATIONS * Viz.NR_POLARIZATIONS];
    private float meanMean[] = new float[Viz.NR_POLARIZATIONS * Viz.NR_POLARIZATIONS];
    private float meanMedian[] = new float[Viz.NR_POLARIZATIONS * Viz.NR_POLARIZATIONS];
    private final int nrChannels;

    PostCorrelationFlaggerHistory(final int nrChannels) {
        this.nrChannels = nrChannels;
        for (int pol = 0; pol < Viz.NR_POLARIZATIONS * Viz.NR_POLARIZATIONS; pol++) {
            history[pol] = new ArrayList<HistoryElement>();
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "INT_BAD_REM_BY_1", justification = "ignore find bugs warning if HISTORY_STEP_SIZE happens to be 1")
    public
            void add(final int pol, final int second, final float mean, final float median, final float stdDev, final float[] freqData) {
        if (second % HISTORY_STEP_SIZE != 0) {
            return;
        }

        final HistoryElement h = new HistoryElement(second, mean, median, stdDev, freqData);
        if (history[pol].size() >= HISTORY_SIZE) {
            meanMean[pol] -= history[pol].get(history[pol].size() - 1).mean;
            meanMedian[pol] -= history[pol].get(history[pol].size() - 1).median;
            history[pol].remove(history[pol].size() - 1);
        }

        history[pol].add(h);
        meanMean[pol] += mean;
        meanMedian[pol] += median;
    }

    float getMeanMean(final int pol) {
        if (history[pol].size() == 0) {
            return 0.0f;
        }
        return meanMean[pol] / history[pol].size();
    }

    float getMeanMedian(final int pol) {
        if (history[pol].size() == 0) {
            return 0.0f;
        }
        return meanMedian[pol] / history[pol].size();
    }

    float getStdDevOfMeans(final int pol) {
        final float meanMean = getMeanMean(pol);
        float stdDev = 0.0f;
        final ArrayList<HistoryElement> h = history[pol];

        for (int i = 0; i < h.size(); i++) {
            final float diff = h.get(i).mean - meanMean;
            stdDev += diff * diff;
        }
        stdDev /= h.size();
        stdDev = (float) Math.sqrt(stdDev);
        return stdDev;
    }

    float getStdDevOfMedians(final int pol) {
        final float meanMedian = getMeanMedian(pol);
        float stdDev = 0.0f;
        final ArrayList<HistoryElement> h = history[pol];

        for (int i = 0; i < h.size(); i++) {
            final float diff = h.get(i).median - meanMedian;
            stdDev += diff * diff;
        }
        stdDev /= h.size();
        stdDev = (float) Math.sqrt(stdDev);
        return stdDev;
    }

    int getSize(final int pol) {
        return history[pol].size();
    }

    float[] getIntegratedPowers(final int pol) {
        final float[] res = new float[nrChannels];

        final ArrayList<HistoryElement> h = history[pol];

        for (int i = 0; i < h.size(); i++) {
            for (int c = 0; c < nrChannels; c++) {
                res[c] += h.get(i).freqData[c];
            }
        }
        return res;
    }
}
