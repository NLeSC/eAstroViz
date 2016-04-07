#ifndef LOFAR_CNPROC_PRE_CORRELATION_NO_CHANNELS_FLAGGER_H
#define LOFAR_CNPROC_PRE_CORRELATION_NO_CHANNELS_FLAGGER_H

#include <Flagger.h>
#include <BandPass.h>
#include <Interface/FilteredData.h>

#if defined HAVE_FFTW3
#include <fftw3.h>
#elif defined HAVE_FFTW2
#include <fftw.h>
#else
#error Should have FFTW3 or FFTW2 installed
#endif

namespace LOFAR {
namespace RTCP {

// The history flagger is based on thresholding. This defines the sensitivity in units of standard deviation.
#define HISTORY_FLAGGER_SENSITIVITY 10.0f

#define DEFAULT_FFT_SIZE 256

// save intermedate, flagged intermediate and replaced intermediate files
#define SAVE_REAL_TIME_FLAGGER_DEBUG 1

#define REPLACE_WITH_ZERO 0
#define REPLACE_WITH_MEAN 1
#define REPLACE_WITH_RANDOM 2
#define REPLACE_WITH_MEDIAN 3

// Choose a way to replace missing data.
#define REPLACEMENT_METHOD REPLACE_WITH_MEDIAN

// Integrate in time until we have itsFFTSize elements.
// Flag on that in time direction.
// Next, do FFT, flag in frequency direction, replace samples with median, inverseFFT.
class PreCorrelationNoChannelsFlagger : public Flagger {
public:
  PreCorrelationNoChannelsFlagger(const Parset& parset, unsigned myPset, unsigned myCoreInPset, 
				  bool correctBandPass, const unsigned nrStations, const unsigned nrSubbands, const unsigned nrChannels, 
				  const unsigned nrSamplesPerIntegration, float cutoffThreshold = 7.0f);

  void flag(FilteredData* filteredData, unsigned globalTime, unsigned currentSubband);

  ~PreCorrelationNoChannelsFlagger();

private:

  void flagStation(FilteredData* filteredData, unsigned globalTime, unsigned station, unsigned subband);
  void flagInFrequencyDirection(FilteredData* filteredData, unsigned globalTime, unsigned station, unsigned subband);
  void flagInTimeDirection(FilteredData* filteredData, unsigned globalTime, unsigned station, unsigned subband);

  void calcIntegratedPowersTime(FilteredData* filteredData, unsigned station, unsigned subband, unsigned pol);
  void calcIntegratedPowersFrequency(FilteredData* filteredData, unsigned station, unsigned subband, unsigned pol);

  void wipeOriginallyFlaggedData(unsigned station, FilteredData* filteredData);
  void wipeFlagsFrequency();
  void wipeFlagsTime();
  void applyFlagsTime(FilteredData* filteredData, unsigned station, unsigned subband, unsigned flaggedCountTime);
  void applyFlagsFrequency(FilteredData* filteredData, unsigned globalTime, unsigned station, unsigned subband, unsigned flaggedCountFrequency);
  unsigned takeUnionOfFlags(vector<vector<bool> >& flags);

  fcomplex computeReplacementValueTime(FilteredData* filteredData, unsigned station, unsigned subband, unsigned pol, unsigned nrFlaggedSamples);
  fcomplex replacementValueTimeSanityCheck(unsigned station, unsigned subband, fcomplex replacementValue);

  fcomplex computeReplacementValueFromHistoryTime(unsigned station, unsigned subband);

  fcomplex computeReplacementValueFrequency(unsigned station, unsigned subband, unsigned pol, unsigned nrFlaggedSamples);

#if SAVE_REAL_TIME_FLAGGER_DEBUG
  FILE* openIntermediate(unsigned myPset, unsigned myCoreInPset, string extension);
  void saveIntermediate(FILE* file, unsigned globalTime, unsigned station, unsigned subband, bool flagData);
#endif

  void initFFT();
  void forwardFFT();
  void backwardFFT();

  vector<fcomplex> itsSamples; // [itsFFTSize]
  vector<vector<float> >itsPowers; // [NR_POLARIZATIONS][itsFFTSize]
  vector<fcomplex> itsFFTBuffer; // [itsFFTSize]
  vector<vector<bool> >itsFlagsTime; // [NR_POLARIZATIONS][itsFFTSize]
  vector<vector<bool> > itsFlagsFrequency; // [NR_POLARIZATIONS][itsFFTSize]

#if defined HAVE_FFTW3
  fftwf_plan itsFFTWforwardPlan, itsFFTWbackwardPlan;
#elif defined HAVE_FFTW2
  fftw_plan  itsFFTWforwardPlan, itsFFTWbackwardPlan;
#endif

  MultiDimArray<FlaggerHistory, 2> itsHistory;   // [nrSations][nrSubbands]
  MultiDimArray<FlaggerHistory, 3> itsHistoryFrequency;   // [nrStations][nrSubbands][nrChannels]

  static const unsigned itsFFTSize = DEFAULT_FFT_SIZE;

  const unsigned itsNrSamplesPerIntegration;
  unsigned itsIntegrationFactor; 

  const bool itsUseHistory;
  const bool itsFlagInFrequencyDirection;
  const bool itsFlagInTimeDirection;

  bool itsCorrectBandPass;
  BandPass itsBandPass;
};

} // namespace RTCP
} // namespace LOFAR

#endif // LOFAR_CNPROC_PRE_CORRELATION_NO_CHANNELS_FLAGGER_H
