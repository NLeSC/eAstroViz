#ifndef LOFAR_CNPROC_POST_CORRELATION_FLAGGER_H
#define LOFAR_CNPROC_POST_CORRELATION_FLAGGER_H

#include <Flagger.h>

namespace LOFAR {
namespace RTCP {

// The history flagger is based on thresholding. This defines the sensitivity in units of standard deviation.
#define HISTORY_FLAGGER_SENSITIVITY 10.0f

class CorrelatedData;
class Parset;

class PostCorrelationFlagger : public Flagger
{
  public:

  // The firstThreshold of 6.0 is taken from Andre's code.
  PostCorrelationFlagger(const Parset& parset, const unsigned nrStations, const unsigned nrSubbands, const unsigned nrChannels,
			 const std::vector<unsigned>& subbandList, const float cutoffThreshold = 7.0f, float baseSentitivity = 1.0f);

  void flag(CorrelatedData* correlatedData, unsigned globalTime, unsigned currentSubband);

  // Tries to detect broken stations.
  void detectBrokenStations();

private:
  void calculatePowers(unsigned baseline, unsigned pol1, unsigned pol2, CorrelatedData* correlatedData);
  void flagBaseline(CorrelatedData* correlatedData, unsigned globalTime, unsigned currentSubband, unsigned baseline);
  unsigned callFlaggerFrequency(unsigned pol1, unsigned pol2);
  void historyFlagger(unsigned globalTime, unsigned currentSubband, unsigned baseline, unsigned& flaggedCount);

  void wipeFlags();
  void applyFlags(unsigned baseline, CorrelatedData* correlatedData);

  void takeUnionOfFlagsFrequency();

  unsigned getSubbandIndex(unsigned subband);

  // Used for detecting broken stations.
  void calculateSummedbaselinePowers(unsigned baseline);
  void wipeSummedPowers();

  const unsigned itsNrBaselines;
  const bool itsDetectBrokenStations;

  // do not make a reference, we need to copy it.
  const std::vector<unsigned> itsSubbandList;

  // TODO move to Flagger?
  const bool itsUseHistory;

  // For the powers, we need to keep the data for both polarizations in memory.
  // We need this for the history flagger, we only know the flags after the SIR operators and after unifying the flags of both polarizations.
  std::vector<std::vector<std::vector<float> > > itsPowers; // [NR_POLARIZATIONS][NR_POLARIZATIONS][nrChannels]
  std::vector<std::vector<std::vector<bool> > > itsFlags; // [NR_POLARIZATIONS][NR_POLARIZATIONS][nrChannels]

  // we basically cannot keep history per channel, the size is just too big.
  MultiDimArray<FlaggerHistory, 2> itsHistory;   // [nrBaselines][nrSubbandsForThisCore]

  std::vector<float> itsSummedBaselinePowers; // [nrBaselines]
  std::vector<float> itsSummedStationPowers;  // [nrStations]

};


} // namespace RTCP
} // namespace LOFAR

#endif // LOFAR_CNPROC_POST_CORRELATION_FLAGGER_H
