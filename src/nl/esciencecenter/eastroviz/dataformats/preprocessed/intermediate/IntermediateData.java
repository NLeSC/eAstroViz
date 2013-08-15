package nl.esciencecenter.eastroviz.dataformats.preprocessed.intermediate;

import nl.esciencecenter.eastroviz.dataformats.preprocessed.PreprocessedData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IntermediateData extends PreprocessedData {
    private static final Logger logger = LoggerFactory.getLogger(IntermediateData.class);

    public IntermediateData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands, final int station) {
        super(fileName, integrationFactor, maxSequenceNr, maxSubbands, new String[] { "X", "Y" }, station, 0);
    }

    @Override
    public String polarizationToString(final int pol) {
        switch (pol) {
        case 0:
            return "X";
        case 1:
            return "Y";
        default:
            return "error";
        }
    }

    @Override
    public int StringToPolarization(final String polString) {
        if (polString.equals("X")) {
            return 0;
        } else if (polString.equals("Y")) {
            return 1;
        } else {
            logger.warn("illegal polarization: " + polString);
            return -1;
        }
    }
}
