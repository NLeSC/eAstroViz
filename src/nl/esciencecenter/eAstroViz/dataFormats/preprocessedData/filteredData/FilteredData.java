package nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.filteredData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.PreprocessedData;

public final class FilteredData extends PreprocessedData {
    private static final Logger logger = LoggerFactory.getLogger(FilteredData.class);

    // FilteredData on BG/p is [nrChannels][nrStations][nrSamplesPerIntegration | 2][NR_POLARIZATIONS]
    public FilteredData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands, final int station, final int pol) {
        super(fileName, integrationFactor, maxSequenceNr, maxSubbands, new String[] { "X", "Y" }, station, pol);
    }
    
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
