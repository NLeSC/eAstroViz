package lofar.dataFormats.preprocessedData.intermediateData;

import java.io.IOException;

import lofar.dataFormats.preprocessedData.PreprocessedData;

public final class IntermediateData extends PreprocessedData {
    public IntermediateData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands) {
        super(fileName, integrationFactor, maxSequenceNr, maxSubbands);
    }

    @Override
    public void read() throws IOException {
        super.read();

        // no longer needed
        /*
                // correct the fftshift
                for (int time = 0; time < nrTimes; time++) {
                    for (int sb = 0; sb < nrSubbands; sb++) {
                        final float[] orig = data[time][sb].clone();
                        final boolean[] origInitialFlags = initialFlagged[time][sb].clone();
                        final boolean[] origFlags = flagged[time][sb].clone();
                        
                        for (int ch = 0; ch < nrChannels; ch++) {
                            data[time][sb][ch] = orig[((nrChannels / 2) + ch) % nrChannels];
                            initialFlagged[time][sb][ch] = origInitialFlags[((nrChannels / 2) + ch) % nrChannels];
                            flagged[time][sb][ch] = origFlags[((nrChannels / 2) + ch) % nrChannels];
                        }
                    }
                }
                */
    }
}
