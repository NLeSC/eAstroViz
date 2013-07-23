package lofar.dataFormats.preprocessedData.filteredData;

import lofar.dataFormats.preprocessedData.PreprocessedData;

public final class FilteredData extends PreprocessedData {

    // FilteredData on BG/p is [nrChannels][nrStations][nrSamplesPerIntegration | 2][NR_POLARIZATIONS]
    public FilteredData(final String fileName, final int integrationFactor, final int maxSequenceNr, final int maxSubbands, final int station) {
        super(fileName, integrationFactor, maxSequenceNr, maxSubbands, new String[] {"X", "Y"}, station);
    }
}
