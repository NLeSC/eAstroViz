package nl.esciencecenter.eastroviz.dataformats.raw;

import java.util.ArrayList;

import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawData {
    static final float PI = (float) Math.PI;

    private static final Logger logger = LoggerFactory.getLogger(BeamFormedData.class);

    private final int nrSubbands;

    private final ArrayList<float[]> dataXR = new ArrayList<float[]>(); // [NR_TIMES][NR_SUBBANDS]
    private final ArrayList<float[]> dataXI = new ArrayList<float[]>(); // [NR_TIMES][NR_SUBBANDS]
    private final ArrayList<float[]> dataYR = new ArrayList<float[]>(); // [NR_TIMES][NR_SUBBANDS]
    private final ArrayList<float[]> dataYI = new ArrayList<float[]>(); // [NR_TIMES][NR_SUBBANDS]

    public RawData(final int nrSubbands) {
        this.nrSubbands = nrSubbands;
    }

    public int getNrSubbands() {
        return nrSubbands;
    }

    public int getNrTimesTime() {
        return dataXR.size();
    }

    public void addFrame(final float[][][][] frame) { // a frame is [NR_SUBBANDS][NR_TIMES][NR_POLARIZATIONS][REAL/IMAG];
        if (nrSubbands != frame.length) {
            throw new RuntimeException("internal error");
        }

        for (int time = 0; time < frame[0].length; time++) {
            final float[] subbandDataXR = new float[nrSubbands];
            final float[] subbandDataXI = new float[nrSubbands];
            final float[] subbandDataYR = new float[nrSubbands];
            final float[] subbandDataYI = new float[nrSubbands];

            for (int sb = 0; sb < nrSubbands; sb++) {
                subbandDataXR[sb] = frame[sb][time][0][0];
                subbandDataXI[sb] = frame[sb][time][0][1];
                subbandDataYR[sb] = frame[sb][time][1][0];
                subbandDataYI[sb] = frame[sb][time][1][1];

                dataXR.add(subbandDataXR);
                dataXI.add(subbandDataXR);
                dataYR.add(subbandDataXR);
                dataYI.add(subbandDataXR);
            }
        }
        logger.info("time samples: " + dataXR.size());
    }

    public float getData(final int time, final int subband, final int polarization, final int realOrComplex) {
        if (polarization == 0) {
            if (realOrComplex == 0) {
                return dataXR.get(time)[subband];
            } else {
                return dataXI.get(time)[subband];
            }
        } else {
            if (realOrComplex == 0) {
                return dataYR.get(time)[subband];
            } else {
                return dataYI.get(time)[subband];
            }
        }
    }

    public float getAmplitude(final int time, final int subband, final int polarization) {
        final float real = getData(time, subband, polarization, 0);
        final float imag = getData(time, subband, polarization, 1);

        final float amplitude = (float) Math.sqrt(real * real + imag * imag);

        return amplitude;
    }

    public float getPhase(final int time, final int subband, final int polarization) {
        final float real = getData(time, subband, polarization, 0);
        final float imag = getData(time, subband, polarization, 1);

        float phase = ((float) Math.atan2(imag, real)) * 360.0f / (2.0f * PI);
        if (phase < 0.0f) {
            phase += 360.0f;
        }

        return phase;
    }
}
