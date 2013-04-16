package lofar.dataFormats.preprocessedData.filteredData;

import lofar.dataFormats.preprocessedData.PreprocessedData;

public final class FilteredData extends PreprocessedData {

    // FilteredData on BG/p is [nrChannels][nrStations][nrSamplesPerIntegration | 2][NR_POLARIZATIONS]
    // We only have 1 station, 1 polarization, and one second is integrated over time.
    // However, we have multiple subbands and seconds
    public FilteredData(final String fileName, final int integrationFactor, final int maxSequenceNr) {
        super(fileName, integrationFactor, maxSequenceNr);
    }
}
