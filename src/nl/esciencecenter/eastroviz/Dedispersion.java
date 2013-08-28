package nl.esciencecenter.eastroviz;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dedispersion {
    public static final boolean COLLAPSE_DEDISPERSED_DATA = false;
    public static final int NR_PERIODS_IN_FOLD = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(Dedispersion.class);

    private static int maximumShift;

    public static int[] computeShifts(int nrSubbands, int nrChannels, float nrSamplesPerSecond, double lowFreq, double freqStep,
            float dm) {
        int nrFrequencies = nrSubbands * nrChannels;

        int[] shifts = new int[nrFrequencies];

        double highFreq = (lowFreq + ((nrFrequencies - 1) * freqStep));
        double inverseHighFreq = 1.0f / (highFreq * highFreq);

        double kDM = 4148.808f * dm;

        for (int freq = 0; freq < nrFrequencies; freq++) {
            double inverseFreq = 1.0f / ((lowFreq + (freq * freqStep)) * (lowFreq + (freq * freqStep)));
            double delta = kDM * (inverseFreq - inverseHighFreq);
            shifts[freq] = (int) (delta * nrSamplesPerSecond);
            LOGGER.debug("inverseFreq = " + inverseFreq + ", delta = " + delta + ", shift = " + shifts[freq]);
        }
        return shifts;
    }

    public static void dedisperse(float[][][] data, boolean[][][] flagged, float nrSamplesPerSecond, double lowFreq,
            double freqStep, float dm) {
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

        if (LOGGER.isDebugEnabled()) {
            for (int i = 0; i < shifts.length; i++) {
                LOGGER.debug("shift " + i + " = " + shifts[i]);
            }
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

        LOGGER.info("nrTimes = " + nrTimes + ", nrSubbands = " + nrSubbands + ", nrSamplesPerSecond = " + nrSamplesPerSecond
                + ", length of folded output = " + res.length);

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
}
