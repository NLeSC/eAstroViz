//# Always #include <lofar_config.h> first!
#include <lofar_config.h>

#include <Common/Timer.h>

#include <Correlator.h>
#include <PostCorrelationFlagger.h>

namespace LOFAR {
namespace RTCP {

static NSTimer flaggerTimer("RFI post flagger", true, true);
static NSTimer flaggerApplyTimer("RFI post correlation flagger apply flags", true, true);

static NSTimer wipeFlagsTimer("RFI post freq wipe flags", true, true);
static NSTimer integrateTimer("RFI post freq integrate", true, true);
static NSTimer callFlaggerTimer("RFI post freq call flagger", true, true);
static NSTimer unionTimer("RFI post freq union", true, true);
static NSTimer sirTimer("RFI post freq SIR", true, true);
static NSTimer historyTimer("RFI post freq history", true, true);


static NSTimer detectBrokenStationsTimer("RFI post DetectBrokenStations", true, true);

// CorrelatedData samples: [nrBaselines][nrChannels][NR_POLARIZATIONS][NR_POLARIZATIONS]

// We have the data for one second, all frequencies in a subband.
// Therefore, we flag in the frequency direction only.
// If we want to flag in the time direction, we do this through the history flagger.
// If one of the polarizations exceeds the threshold, flag them all.
// Autocorrelations are ignored; see Andre Offringa's remark on this in his thesis. TODO
// All baselines are flagged completely independently.

PostCorrelationFlagger::PostCorrelationFlagger(const Parset& parset, const unsigned nrStations, const unsigned nrSubbands, 
					       const unsigned nrChannels, const std::vector<unsigned>& subbandList, const float cutoffThreshold, float baseSentitivity) :
  Flagger(parset, nrStations, nrSubbands, nrChannels, cutoffThreshold, baseSentitivity,
	  Flagger::getFlaggerType(parset.onlinePostCorrelationFlaggingType(Flagger::getFlaggerTypeString(FLAGGER_SUM_THRESHOLD))), 
	  getFlaggerStatisticsType(parset.onlinePostCorrelationFlaggingStatisticsType(getFlaggerStatisticsTypeString(FLAGGER_STATISTICS_WINSORIZED)))), 
  itsNrBaselines((nrStations * (nrStations + 1) / 2)),
  itsDetectBrokenStations(parset.onlinePostCorrelationFlaggingDetectBrokenStations()),
  itsSubbandList(subbandList),
  itsUseHistory(parset.onlinePostCorrelationFlaggingUseHistory())
 {
  itsPowers.resize(NR_POLARIZATIONS);
  for(int pol=0; pol < NR_POLARIZATIONS; pol++) {
    itsPowers[pol].resize(NR_POLARIZATIONS);
  }
  for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
    for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
      itsPowers[pol1][pol2].resize(itsNrChannels);
    }
  }

  itsFlags.resize(NR_POLARIZATIONS);
  for(int pol=0; pol < NR_POLARIZATIONS; pol++) {
    itsFlags[pol].resize(NR_POLARIZATIONS);
  }
  for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
    for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
      itsFlags[pol1][pol2].resize(itsNrChannels);
    }
  }

  if(itsDetectBrokenStations) {
    itsSummedBaselinePowers.resize(itsNrBaselines);
    itsSummedStationPowers.resize(itsNrStations);
  }

  if(itsUseHistory) {
    itsHistory.resize(boost::extents[itsNrBaselines][subbandList.size()]);
  }

  LOG_DEBUG_STR("post correlation flagging type = " << getFlaggerTypeString()
		<< ", statistics type = " << getFlaggerStatisticsTypeString());
}

void PostCorrelationFlagger::flag(CorrelatedData* correlatedData, unsigned globalTime, unsigned currentSubband) {
  flaggerTimer.start();

  if(itsDetectBrokenStations) {
    wipeSummedPowers();
  }

  for (unsigned baseline = 0; baseline < itsNrBaselines; baseline++) {
    if (Correlator::baselineIsAutoCorrelation(baseline)) { // skip autocorrelations
      continue;
    }

    flagBaseline(correlatedData, globalTime, currentSubband, baseline);

    if(itsDetectBrokenStations) {
      calculateSummedbaselinePowers(baseline);
    }
  }
  flaggerTimer.stop();

  if(globalTime == 952) {
    LOG_DEBUG_STR(flaggerTimer);
    LOG_DEBUG_STR(flaggerApplyTimer);

    LOG_DEBUG_STR(wipeFlagsTimer);
    LOG_DEBUG_STR(integrateTimer);
    LOG_DEBUG_STR(callFlaggerTimer);
    LOG_DEBUG_STR(unionTimer);
    LOG_DEBUG_STR(sirTimer);
    LOG_DEBUG_STR(historyTimer);
  }
}


void PostCorrelationFlagger::flagBaseline(CorrelatedData* correlatedData, unsigned globalTime, unsigned currentSubband, unsigned baseline) {
  wipeFlagsTimer.start();
  wipeFlags();
  wipeFlagsTimer.stop();

  for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
    for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
      integrateTimer.start();
      calculatePowers(baseline, pol1, pol2, correlatedData);
      integrateTimer.stop();
      
      // Flag twice, the second time with corrected statistics
      callFlaggerTimer.start();
      unsigned flagged1 = callFlaggerFrequency(pol1, pol2);
      unsigned flagged2 = 0;
      if(flagged1 > 0) {
	flagged2 = callFlaggerFrequency(pol1, pol2);
      }
      callFlaggerTimer.stop();
//      LOG_DEBUG_STR("post correlation flagger: second " << globalTime << " for baseline " << baseline << " subband " << currentSubband
//		    << " pol1 " << pol1 << " pol2 " << pol2 << " flagged in frequency direction pass 1: " << flagged1 << ", pass 2: " << flagged2);
    }
  }
    
  // Compute the union of flags of the polarizations
  unionTimer.start();
  takeUnionOfFlagsFrequency();
  unionTimer.stop();
  
  // Scale-Invariant-Rank operator, to expand the flagged windows a bit, and to fill in the holes.
  sirTimer.start();
  unsigned flaggedCount = SIROperator(itsFlags[0][0], 0.4f); 
  sirTimer.stop();
  
//  LOG_DEBUG_STR("post correlation flagger: second " << globalTime << " for baseline " << baseline << " subband " << currentSubband
//		<< " total flagged in frequency direction: " << flaggedCount);
    
  if(itsUseHistory) {
    historyTimer.start();
    historyFlagger(globalTime, currentSubband, baseline, flaggedCount);
    historyTimer.stop();
  }

  if(flaggedCount > 0) {
    flaggerApplyTimer.start();
    applyFlags(baseline, correlatedData);
    flaggerApplyTimer.stop();
  }
}


unsigned PostCorrelationFlagger::getSubbandIndex(unsigned subband) {
  for(unsigned currentSubbandIndex=0; currentSubbandIndex<itsSubbandList.size(); currentSubbandIndex++) {
//    LOG_DEBUG_STR("subband at index " << currentSubbandIndex << " is " << itsSubbandList[currentSubbandIndex]);

    if(itsSubbandList[currentSubbandIndex] == subband) {
      return currentSubbandIndex;
    }
  }

  LOG_WARN_STR("post correlation flagger: subband not found, skipping history flagger");
  return itsNrSubbands;
}


unsigned PostCorrelationFlagger::callFlaggerFrequency(unsigned pol1, unsigned pol2) {
  switch(itsFlaggerType) {
  case FLAGGER_THRESHOLD:
    return thresholdingFlagger1D(itsPowers[pol1][pol2], itsFlags[pol1][pol2]);
    break;
  case FLAGGER_SUM_THRESHOLD:
    return sumThresholdFlagger1D(itsPowers[pol1][pol2], itsFlags[pol1][pol2], itsBaseSensitivity);
    break;
  default:
    LOG_WARN_STR("ERROR, illegal FlaggerType. Skipping online post correlation flagger.");
    return -1;
  }
}


void PostCorrelationFlagger::historyFlagger(unsigned globalTime, unsigned currentSubband, unsigned baseline, unsigned& flaggedCount) {
    unsigned subbandIndex = getSubbandIndex(currentSubband);
    if(subbandIndex < itsNrSubbands) {
      // compute val
      float mean = 0.0f;
      for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
	for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
	  float tmpMean;
	  calculateWinsorizedMean(itsPowers[pol1][pol2], itsFlags[0][0], tmpMean);
	  mean += tmpMean;
	}
      }

      mean /= NR_POLARIZATIONS * NR_POLARIZATIONS * itsNrChannels;

      if(addToHistory(mean, itsHistory[baseline][subbandIndex], HISTORY_FLAGGER_SENSITIVITY)) {
//	LOG_DEBUG_STR("PostCorrelationFlagger: History frequency flagger flagged this second " << globalTime 
//		      << " for baseline " << baseline << " subband " << currentSubband);

	for(unsigned channel=0; channel<itsNrChannels; channel++) {
	  if(!itsFlags[0][0][channel]) {
	    flaggedCount++;
	    itsFlags[0][0][channel] = true;
	  }
	}
      }
    }
}


void PostCorrelationFlagger::wipeFlags() {
  for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
    for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
      for (unsigned channel = 0; channel < itsNrChannels; channel++) {
	itsFlags[pol1][pol2][channel] = false;
      }
    }
  }
}


void PostCorrelationFlagger::applyFlags(unsigned baseline, CorrelatedData* correlatedData) {
  for (unsigned channel = 0; channel < itsNrChannels; channel++) {
    if (itsFlags[0][0][channel]) { // take pol 0,0. All flags are unified in the first polarization.
      correlatedData->setNrValidSamples(baseline, channel, 0);
    }
  }
}


void PostCorrelationFlagger::calculatePowers(unsigned baseline, unsigned pol1, unsigned pol2, CorrelatedData* correlatedData) {
  // No need to check and correct for the nr of valid samples. The correlator already corrects for this.
  for (unsigned channel = 0; channel < itsNrChannels; channel++) {
    fcomplex sample = correlatedData->visibilities[baseline][channel][pol1][pol2];
    float power = real(sample) * real(sample) + imag(sample) * imag(sample);
    itsPowers[pol1][pol2][channel] = power;
  }
}


void PostCorrelationFlagger::takeUnionOfFlagsFrequency() {
  for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
    for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
      for (unsigned channel = 0; channel < itsNrChannels; channel++) {
	itsFlags[0][0][channel] = itsFlags[0][0][channel] | itsFlags[pol1][pol2][channel];
      }
    }
  }
}


void PostCorrelationFlagger::calculateSummedbaselinePowers(unsigned baseline) {
  for (unsigned pol1 = 0; pol1 < NR_POLARIZATIONS; pol1++) {
    for (unsigned pol2 = 0; pol2 < NR_POLARIZATIONS; pol2++) {
      for (unsigned channel = 0; channel < itsNrChannels; channel++) {
	if (!itsFlags[pol1][pol2][channel]) {
	  itsSummedBaselinePowers[baseline] += itsPowers[pol1][pol2][channel];
	}
      }
    }
  }
}


// TODO: also integrate flags?
void PostCorrelationFlagger::detectBrokenStations() {
  detectBrokenStationsTimer.start();

  // Sum all baselines that involve a station (both horizontally and vertically).

  for (unsigned station = 0; station < itsNrStations; station++) {
    float sum = 0.0f;
    for (unsigned stationH = station+1; stationH < itsNrStations; stationH++) { // do not count autocorrelation
      unsigned baseline = Correlator::baseline(station, stationH);
      sum += itsSummedBaselinePowers[baseline];
    }
    for (unsigned stationV = 0; stationV < station; stationV++) {
      unsigned baseline = Correlator::baseline(stationV, station);
      sum += itsSummedBaselinePowers[baseline];
    }

    itsSummedStationPowers[station] = sum;
  }

  float stdDev;
  float mean;
  calculateMeanAndStdDev(itsSummedStationPowers, mean, stdDev);

  float median;
  calculateMedian(itsSummedStationPowers, median);
  float threshold = mean + itsCutoffThreshold * stdDev;

//  LOG_DEBUG_STR("RFI post detectBrokenStations: mean = " << mean << ", median = " << median << " stdDev = " << stdDev << ", threshold = " << threshold);

//  for (unsigned station = 0; station < itsNrStations; station++) {
//    LOG_DEBUG_STR("RFI post detectBrokenStations: station " << station << " total summed power = " << itsSummedStationPowers[station]);
//    if (itsSummedStationPowers[station] > threshold) {
//      LOG_INFO_STR(
//          "RFI post detectBrokenStations: WARNING, station " << station << " seems to be corrupted, total summed power = " << itsSummedStationPowers[station]);
//    }
//  }

  detectBrokenStationsTimer.stop();
}


void PostCorrelationFlagger::wipeSummedPowers() {
  for (unsigned baseline = 0; baseline < itsNrBaselines; baseline++) {
    itsSummedBaselinePowers[baseline] = 0.0f;
  }

  for (unsigned station = 0; station < itsNrStations; station++) {
    itsSummedStationPowers[station] = 0.0f;
  }
}

} // namespace RTCP
} // namespace LOFAR
