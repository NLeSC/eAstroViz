#ifndef LOFAR_CNPROC_PRE_CORRELATION_FLAGGER_H
#define LOFAR_CNPROC_PRE_CORRELATION_FLAGGER_H

#include <Flagger.h>
#include <Interface/FilteredData.h>

namespace LOFAR {
namespace RTCP {

// The history flagger is based on thresholding. This defines the sensitivity in units of standard deviation.
#define HISTORY_FLAGGER_SENSITIVITY 10.0f

  // If > 256 channels, we are ok, and fully integrate in time. We have enough samples to flag in the frequency direction.
  // If < 256 channels, we will have multiple (e.g. 16) samples per block (nrSamplesPerIntegration is always a multiple of 16).
#define MINIMUM_NR_CHANNELS_FOR_1D 256

// If we only have few channels (e.g., 16), we have to do 2D flagging, otherwise we don't have enough data to do statistics.
// So, we only partially integrate in the time direction.
class PreCorrelationFlagger : public Flagger {
  public:
  PreCorrelationFlagger(const Parset& parset, const unsigned nrStations, const unsigned nrSubbands, const unsigned nrChannels, const unsigned nrSamplesPerIntegration, float cutoffThreshold = 7.0f);

  void flag(FilteredData* filteredData, unsigned globalTime, unsigned currentSubband);

  private:

  void flagStation(FilteredData* filteredData, unsigned station, unsigned globalTime, unsigned currentSubband);

  unsigned flagInFrequencyDirection(FilteredData* filteredData, unsigned station, unsigned globalTime, unsigned currentSubband);
  unsigned callFlaggerFrequency(unsigned station, unsigned pol);
  void integratePowersFrequency(unsigned station, unsigned pol, FilteredData* filteredData);
  void wipeFlagsFrequency();
  void takeUnionOfFlagsFrequency();
  void storeFlagsFrequency(unsigned station, FilteredData* filteredData);
  void wipeFlaggedDataFrequency(unsigned station, FilteredData* filteredData);

  unsigned flagInTimeDirection(FilteredData* filteredData, unsigned station, unsigned globalTime, unsigned currentSubband);
  unsigned callFlaggerTime(unsigned station, unsigned pol);
  void integratePowersTime(unsigned station, unsigned pol, FilteredData* filteredData);
  void wipeFlagsTime();
  void takeUnionOfFlagsTime();
  void applyFlagsTime(unsigned station, FilteredData* filteredData);

  // TODO move to Flagger?
  const bool itsUseHistory;
  const bool itsFlagInFrequencyDirection;
  const bool itsFlagInTimeDirection;

  const unsigned itsNrSamplesPerIntegration;
  unsigned itsIntegrationFactor;  
  unsigned itsNrBlocks;

  // For the powers, we need to keep the data for both polarizations in memory.
  // We need this for the history flagger, we only know the flags after the SIR operators and after unifying the flags of both polarizations.
  std::vector<MultiDimArray<float,2> > itsIntegratedPowersFrequency; // [NR_POLARIZATIONS][itsNrChannels][itsNrBlocks]
  std::vector<MultiDimArray<bool,2> > itsIntegratedFlagsFrequency; // [NR_POLARIZATIONS][itsNrChannels][itsNrBlocks]

  std::vector<std::vector<float> > itsIntegratedPowersTime; // [NR_POLARIZATIONS][itsNrSamplesPerIntegration]
  std::vector<std::vector<bool> > itsIntegratedFlagsTime; // [NR_POLARIZATIONS][itsNrSamplesPerIntegration]

  MultiDimArray<FlaggerHistory, 2> itsHistory;   // [nrStations][nrSubbands]
  MultiDimArray<FlaggerHistory, 3> itsHistoryFrequency;   // [nrStations][nrSubbands][nrChannels]
};

} // namespace RTCP
} // namespace LOFAR

#endif // LOFAR_CNPROC_PRE_CORRELATION_FLAGGER_H
