//# Always #include <lofar_config.h> first!
#include <lofar_config.h>

#include <Common/Timer.h>

#include <PreCorrelationFlagger.h>

// history is kept per subband, as we can get different subbands over time on this compute node.
// Always flag poth polarizations seperately, and then take the union.

// Do not use the original coarse flags to initialize the flags.
// Since we integrate all time samples, if any sample is flagged in time, we would flag everything.
// This is especially the case for larger numbers of channels, where we only have one block.
// The solution is to just set all samples that were flagged previously by an earlier stage in the pipeline to zero.
// This can happen due to dropped UDP packets for example.
// Adittionally, we cannot copy the original flags to our local data structure, since we use the SIR operator to make the flagged areas
// a bit wider. This is not desirable for the initially flagged data, since the flagging is perfectly precise in that case.
// We know exactly which data is lost due to dropped UDP packets.
// Also, it is not needed to first clear the original flags and then rebuild them. We can just include the newly flagged samples.
// In the input flags, the flagged ranges are stored in channel 1 and copied to the other channels in the PPF.
// Channel 0 is always completely flagged. (If there is more than 1 channel.)
// After we have flagged in the freqyency direction, we just set the flagged samples to zero.
// We do not copy them to the original flags in filtered data immediately, because that would influence the time direction flagger.
// Again, in that case, all samples in frequency are integrated, and if one channel is flagged, they would all be.
// Finally, for both time and frequency, copy the flags back to original data structure (sparse set).

// UPDATE 19/09/2013: If we integrate in time for freq direction, divide by the actual number of samples integrated. Now, if data is dropped due to UDP packet loss,
// these integrated samples are added as 0, leading to a too low integrated value.

// TODO: for the frequency history flagger, we store the mean of the unflagged powers, while in time, we store the winsorized mean of the unflagged samples.

// TODO history: keep list of subbands, not all.

namespace LOFAR {
namespace RTCP {

static NSTimer flaggerTimer("RFI pre flagger", true, true);
static NSTimer flaggerTimeTimer("RFI pre correlation time flagger", true, true);
static NSTimer flaggerFrequencyTimer("RFI pre correlation frequency flagger", true, true);
static NSTimer flaggerApplyTimer("RFI pre correlation flagger apply flags", true, true);

static NSTimer wipeFlagsFreqTimer("RFI pre freq wipe flags", true, true);
static NSTimer integrateFreqTimer("RFI pre freq integrate", true, true);
static NSTimer callFlaggerFreqTimer("RFI pre freq call flagger", true, true);
static NSTimer unionFreqTimer("RFI pre freq union", true, true);
static NSTimer sirFreqTimer("RFI pre freq SIR", true, true);
static NSTimer historyFreqTimer("RFI pre freq history", true, true);
static NSTimer wipeDataFreqTimer("RFI pre freq wipe data", true, true);

static NSTimer wipeFlagsTimeTimer("RFI pre time wipe flags", true, true);
static NSTimer integrateTimeTimer("RFI pre time integrate", true, true);
static NSTimer callFlaggerTimeTimer("RFI pre time call flagger", true, true);
static NSTimer unionTimeTimer("RFI pre time union", true, true);
static NSTimer sirTimeTimer("RFI pre time SIR", true, true);
static NSTimer historyTimeTimer("RFI pre time history", true, true);
  

PreCorrelationFlagger::PreCorrelationFlagger(const Parset& parset, const unsigned nrStations, const unsigned nrSubbands, const unsigned nrChannels, 
					     const unsigned nrSamplesPerIntegration, const float cutoffThreshold)
:
  Flagger(parset, nrStations, nrSubbands, nrChannels, cutoffThreshold, /*baseSentitivity*/ 1.0f, 
	  getFlaggerType(parset.onlinePreCorrelationFlaggingType(getFlaggerTypeString(FLAGGER_SUM_THRESHOLD))), 
	  getFlaggerStatisticsType(parset.onlinePreCorrelationFlaggingStatisticsType(getFlaggerStatisticsTypeString(FLAGGER_STATISTICS_WINSORIZED)))),
  itsUseHistory(parset.onlinePreCorrelationFlaggingUseHistory()),
  itsFlagInFrequencyDirection(parset.onlinePreCorrelationFlaggingFrequencyDirection()),
  itsFlagInTimeDirection(parset.onlinePreCorrelationFlaggingTimeDirection()),
  itsNrSamplesPerIntegration(nrSamplesPerIntegration),
  itsIntegrationFactor(parset.onlinePreCorrelationFlaggingIntegration())
{
  // If not set, or illegal, we set the integration factor in a "smart" way. Just make sure we have enough samples. 
  // If > MINIMUM_NR_CHANNELS_FOR_1D channels, just keep, and fully integrate.
  // If < MINIMUM_NR_CHANNELS_FOR_1D channels, have 16 samples per block (nrSamplesPerIntegration is always a multiple of 16).
  int defaultIntegrationFactor = itsNrSamplesPerIntegration / 16;
  if(nrChannels >= MINIMUM_NR_CHANNELS_FOR_1D) {
    defaultIntegrationFactor = itsNrSamplesPerIntegration;
  }

  if(itsIntegrationFactor == 0) {
    itsIntegrationFactor = defaultIntegrationFactor;
    LOG_INFO_STR("preCorrelationFlagger: integration factor not set, using default value of " << defaultIntegrationFactor);
  }

  if(itsNrSamplesPerIntegration % itsIntegrationFactor != 0) {
    LOG_ERROR_STR("preCorrelationFlagger: Illegal integration factor, using default of " << defaultIntegrationFactor);
    itsIntegrationFactor = defaultIntegrationFactor;
  }

  itsNrBlocks = itsNrSamplesPerIntegration / itsIntegrationFactor;

  itsIntegratedPowersFrequency.resize(NR_POLARIZATIONS);
  for(int pol=0; pol < NR_POLARIZATIONS; pol++) {
    itsIntegratedPowersFrequency[pol].resize(boost::extents[itsNrChannels][itsNrBlocks]);
  }

  itsIntegratedFlagsFrequency.resize(NR_POLARIZATIONS);
  for(int pol=0; pol < NR_POLARIZATIONS; pol++) {
    itsIntegratedFlagsFrequency[pol].resize(boost::extents[itsNrChannels][itsNrBlocks]);
  }

  itsIntegratedPowersTime.resize(NR_POLARIZATIONS);
  for(int pol=0; pol < NR_POLARIZATIONS; pol++) {
    itsIntegratedPowersTime[pol].resize(itsNrSamplesPerIntegration);
  }

  itsIntegratedFlagsTime.resize(NR_POLARIZATIONS);
  for(int pol=0; pol < NR_POLARIZATIONS; pol++) {
    itsIntegratedFlagsTime[pol].resize(itsNrSamplesPerIntegration);
  }

  if(itsUseHistory) {
    itsHistory.resize(boost::extents[itsNrStations][nrSubbands]);
    itsHistoryFrequency.resize(boost::extents[itsNrStations][nrSubbands][itsNrChannels]);
  }

  LOG_DEBUG_STR("pre correlation flagging type = " << getFlaggerTypeString()
		<< ", statistics type = " << getFlaggerStatisticsTypeString()
		<< ", integration factor = " << itsIntegrationFactor
		<< ", nr blocks = " << itsNrBlocks);
}


void PreCorrelationFlagger::flag(FilteredData* filteredData, unsigned globalTime, unsigned currentSubband)
{
  flaggerTimer.start();

  for(unsigned station = 0; station < itsNrStations; station++) {
    flagStation(filteredData, station, globalTime, currentSubband);
  }

  flaggerTimer.stop();

  if(globalTime == 952) {
    LOG_DEBUG_STR(flaggerTimer);
    LOG_DEBUG_STR(flaggerTimeTimer);
    LOG_DEBUG_STR(flaggerFrequencyTimer);
    LOG_DEBUG_STR(flaggerApplyTimer);

    LOG_DEBUG_STR(wipeFlagsFreqTimer);
    LOG_DEBUG_STR(integrateFreqTimer);
    LOG_DEBUG_STR(callFlaggerFreqTimer);
    LOG_DEBUG_STR(unionFreqTimer);
    LOG_DEBUG_STR(sirFreqTimer);
    LOG_DEBUG_STR(historyFreqTimer);
    LOG_DEBUG_STR(wipeDataFreqTimer);

    LOG_DEBUG_STR(wipeFlagsTimeTimer);
    LOG_DEBUG_STR(integrateTimeTimer);
    LOG_DEBUG_STR(callFlaggerTimeTimer);
    LOG_DEBUG_STR(unionTimeTimer);
    LOG_DEBUG_STR(sirTimeTimer);
    LOG_DEBUG_STR(historyTimeTimer);
  }
}


void PreCorrelationFlagger::flagStation(FilteredData* filteredData, unsigned station, unsigned globalTime, unsigned currentSubband)
{
  unsigned flaggedCountFrequency = 0;
  unsigned flaggedCountTime = 0;

  if(itsFlagInFrequencyDirection) {
    flaggerFrequencyTimer.start();
    flaggedCountFrequency = flagInFrequencyDirection(filteredData, station, globalTime, currentSubband);
    flaggerFrequencyTimer.stop();
  }

  if(itsFlagInTimeDirection) {
    flaggerTimeTimer.start();
    flaggedCountTime = flagInTimeDirection(filteredData, station, globalTime, currentSubband);
    flaggerTimeTimer.stop();
  }

  // and now copy all flags back into filteredData
  flaggerApplyTimer.start();
  if(itsFlagInFrequencyDirection && flaggedCountFrequency > 0) { // Only apply if something was flagged. Optimize the cricical path.
    storeFlagsFrequency(station, filteredData); // store flags in filteredData, the data itself was already wiped.
  }

  if(itsFlagInTimeDirection && flaggedCountTime > 0) { // Only apply if something was flagged. Optimize the cricical path.
    applyFlagsTime(station, filteredData); // store flags in filteredData, and wipe flagged data
  }
  flaggerApplyTimer.stop();
}


// The flagger in frequency direction can also flag samples in the time direction. This is because the nrBlocks may be > 1.
unsigned PreCorrelationFlagger::flagInFrequencyDirection(FilteredData* filteredData, unsigned station, unsigned globalTime, unsigned currentSubband)
{
  wipeFlagsFreqTimer.start();
  wipeFlagsFrequency();
  wipeFlagsFreqTimer.stop();
  

  for (unsigned pol = 0; pol < NR_POLARIZATIONS; pol++) {
    integrateFreqTimer.start();
    integratePowersFrequency(station, pol, filteredData);
    integrateFreqTimer.stop();
    
    // Flag twice, the second time with corrected statistics
    callFlaggerFreqTimer.start();
    unsigned extraFlagged1 = callFlaggerFrequency(station, pol);
//    unsigned extraFlagged2 = 0;
//    if(extraFlagged1 > 0) {
//      extraFlagged2 = callFlaggerFrequency(station, pol);
//    }
    callFlaggerFreqTimer.stop();
//    LOG_DEBUG_STR("pre correlation flagger: second " << globalTime << " for station " << station << " subband " << currentSubband
//		  << " pol " << pol << " flagged in frequency direction pass 1: " << extraFlagged1 << ", pass 2: " << extraFlagged2);
  }


  
  // Compute the union of flags of the polarizations
  unionFreqTimer.start();
  takeUnionOfFlagsFrequency();
  unionFreqTimer.stop();

 
  // Scale-Invariant-Rank operator, to expand the flagged windows a bit, and to fill in the holes.
//  sirFreqTimer.start();
//  unsigned flaggedCountFrequency = SIROperator2D(itsIntegratedFlagsFrequency[0], 0.4f); 
//  sirFreqTimer.stop();
  
//  LOG_DEBUG_STR("pre correlation flagger: second " << globalTime << " for station " << station << " subband " << currentSubband
//		<< " total flagged in frequency direction: " << flaggedCountFrequency);
/*
  if(itsUseHistory) {
    historyFreqTimer.start();
    for(unsigned channel = 0; channel < itsNrChannels; channel++) {
      // calculate the mean of the unflagged data (for both polarizations)
      float meanPower = 0.0f;
      unsigned count = 0;
      for(unsigned pol=0; pol<NR_POLARIZATIONS; pol++) {
	for(unsigned block=0; block<itsNrBlocks; block++) {
	  if(!itsIntegratedFlagsFrequency[0][channel][block]) {
	    meanPower += itsIntegratedPowersFrequency[pol][channel][block];
	    count++;
	  }
	}
      }
      
      if(count > 0) {
	meanPower /= (count * itsIntegrationFactor); // mean power per sample.
      
	if(addToHistory(meanPower, itsHistoryFrequency[station][currentSubband][channel], HISTORY_FLAGGER_SENSITIVITY)) {
//	  LOG_DEBUG_STR("History frequency flagger flagged this second " << globalTime 
//			<< " for station " << station << " subband " << currentSubband << " channel " << channel);
	  for(unsigned block=0; block<itsNrBlocks; block++) {
	    if(!itsIntegratedFlagsFrequency[0][channel][block]) {
	      flaggedCountFrequency++;
	      itsIntegratedFlagsFrequency[0][channel][block] = true;
	    }
	  }
	}
      }
    }
    historyFreqTimer.stop();
  }
*/
//  if(flaggedCountFrequency > 0) { // Only apply if someting was flagged. Optimize the cricical path.
    wipeDataFreqTimer.start();
    wipeFlaggedDataFrequency(station, filteredData); // set flagged samples to zero
    wipeDataFreqTimer.stop();
//  }

  /// TODO @@@ remove
  unsigned flaggedCountFrequency = 0;
    for (unsigned channel = 0; channel < itsNrChannels; channel++) {
      for(unsigned block = 0; block < itsNrBlocks; block++) {
	if(itsIntegratedFlagsFrequency[0][channel][block]) flaggedCountFrequency++;
      }
    }

  return flaggedCountFrequency;
}


unsigned PreCorrelationFlagger::flagInTimeDirection(FilteredData* filteredData, unsigned station, unsigned globalTime, unsigned currentSubband)
{
  wipeFlagsTimeTimer.start();
  wipeFlagsTime();
  wipeFlagsTimeTimer.stop();

  for (unsigned pol = 0; pol < NR_POLARIZATIONS; pol++) {
    integrateTimeTimer.start();
    integratePowersTime(station, pol, filteredData);
    integrateTimeTimer.stop();
    
    // Flag twice, the second time with corrected statistics
    // Optimization: this only makes sense is we flagged something the first time!
    callFlaggerTimeTimer.start();
    unsigned extraFlagged1 = callFlaggerTime(station, pol);
//    unsigned extraFlagged2 = 0;
//    if(extraFlagged1 > 0) {
//      extraFlagged2 = callFlaggerTime(station, pol);
//    }
    callFlaggerTimeTimer.stop();
//    LOG_DEBUG_STR("pre correlation flagger: second " << globalTime << " for station " << station << " subband " << currentSubband
//		  << " pol " << pol << " flagged in time direction pass 1: " << extraFlagged1 << ", pass 2: " << extraFlagged2);
  }
  
  // Compute the union of flags of the polarizations
  unionTimeTimer.start();
  takeUnionOfFlagsTime();
  unionTimeTimer.stop();

  // Scale-Invariant-Rank operator, to expand the flagged windows a bit, and to fill in the holes.
//  sirTimeTimer.start();
//  unsigned flaggedCountTime = SIROperator(itsIntegratedFlagsTime[0], 0.4f); 
//  sirTimeTimer.stop();

//  LOG_DEBUG_STR("pre correlation flagger: second " << globalTime << " for station " << station << " subband " << currentSubband
//		<< " total flagged in time direction: " << flaggedCountTime);
/*  
  if(itsUseHistory && flaggedCountTime < itsNrSamplesPerIntegration) { // If everything was already flagged, skip this entirely.
    historyTimeTimer.start();
    // I have empirically found that the mean of the unflagged samples is a better predictor than the median, for history flagging at least.
    float mean0, mean1;
    calculateWinsorizedMean(itsIntegratedPowersTime[0], itsIntegratedFlagsTime[0], mean0);
    calculateWinsorizedMean(itsIntegratedPowersTime[1], itsIntegratedFlagsTime[0], mean1); // take flags at index 0, they are unified.
    float mean = (mean0 + mean1)/(2.0f * itsNrChannels); // divide by nrChannels, since we integrated nrChannels samples.

    if(addToHistory(mean, itsHistory[station][currentSubband], HISTORY_FLAGGER_SENSITIVITY)) {
//      LOG_DEBUG_STR("History time flagger flagged this second " << globalTime << " for station " << station << " subband " << currentSubband);
      const fcomplex zero = makefcomplex(0.0f, 0.0f);
      
      for(unsigned channel = 0; channel < itsNrChannels; channel++) {
	filteredData->flags[channel][station].include(0, itsNrSamplesPerIntegration);
	for(unsigned time=0; time < itsNrSamplesPerIntegration; time++) {
	  for(unsigned pol=0; pol < NR_POLARIZATIONS; pol++) {
	    filteredData->samples[channel][station][time][pol] = zero;
	  }
	}
      }
      
      // We have already wiped and included everyting, so return -1 to make sure the caller does not wipe again.
      historyTimeTimer.stop();
      return -1;
    }
    historyTimeTimer.stop();
  }
*/
//  LOG_DEBUG_STR("pre correlation flagger: second " << globalTime << " for station " << station << " subband " << currentSubband
//		<< " flagged in time direction: " << flaggedCountTime);


  /// TODO @@@ remove
  unsigned flaggedCountTime = 0;
  for (unsigned time = 0; time < itsNrSamplesPerIntegration; time++) {
    if(itsIntegratedFlagsTime[0][time]) flaggedCountTime++;
  }

  return flaggedCountTime;
}


unsigned PreCorrelationFlagger::callFlaggerFrequency(unsigned station, unsigned pol)
{
  (void) station; // avoid compiler warning

  switch(itsFlaggerType) {
  case FLAGGER_THRESHOLD:
    return thresholdingFlagger2D(itsIntegratedPowersFrequency[pol], itsIntegratedFlagsFrequency[pol]);
    break;
  case FLAGGER_SUM_THRESHOLD:
    return sumThresholdFlagger2D(itsIntegratedPowersFrequency[pol], itsIntegratedFlagsFrequency[pol], itsBaseSensitivity);
    break;
  default:
    LOG_INFO_STR("ERROR, illegal FlaggerType. Skipping online pre correlation flagger.");
    return -1;
  }
}


unsigned PreCorrelationFlagger::callFlaggerTime(unsigned station, unsigned pol)
{
  (void) station; // avoid compiler warning

  switch(itsFlaggerType) {
  case FLAGGER_THRESHOLD:
    return thresholdingFlagger1D(itsIntegratedPowersTime[pol], itsIntegratedFlagsTime[pol]);
    break;
  case FLAGGER_SUM_THRESHOLD:
    return sumThresholdFlagger1D(itsIntegratedPowersTime[pol], itsIntegratedFlagsTime[pol], itsBaseSensitivity);
    break;
  default:
    LOG_INFO_STR("ERROR, illegal FlaggerType. Skipping online pre correlation flagger.");
    return -1;
  }
}


void PreCorrelationFlagger::integratePowersFrequency(unsigned station, unsigned pol, FilteredData* filteredData)
{
  // Sum powers over time to increase the signal-to-noise-ratio.
  // We do this in groups of itsIntegrationFactor.

  for (unsigned channel = 0; channel < itsNrChannels; channel++) {
    for(unsigned block = 0; block < itsNrBlocks; block++) {
      float powerSum = 0.0f;
      unsigned count = 0;
      for (unsigned time = 0; time < itsIntegrationFactor; time++) {
	unsigned globalIndex = block * itsIntegrationFactor + time;
	if(!filteredData->flags[channel][station].test(globalIndex)) {
	  fcomplex sample = filteredData->samples[channel][station][globalIndex][pol];
	  powerSum += power(sample);
	  count++;
	}

      }
      itsIntegratedPowersFrequency[pol][channel][block] = powerSum / count;
    }
   }
}


void PreCorrelationFlagger::takeUnionOfFlagsFrequency()
{
  for(unsigned pol=1; pol < NR_POLARIZATIONS; pol++) {
    for (unsigned channel = 0; channel < itsNrChannels; channel++) {
      for(unsigned block = 0; block < itsNrBlocks; block++) {
	itsIntegratedFlagsFrequency[0][channel][block] |= itsIntegratedFlagsFrequency[pol][channel][block];
      }
    }
  }
}


void PreCorrelationFlagger::wipeFlagsFrequency() {
  for(unsigned pol=0; pol < NR_POLARIZATIONS; pol++) {
    for (unsigned channel = 0; channel < itsNrChannels; channel++) {
      for(unsigned block = 0; block < itsNrBlocks; block++) {
	itsIntegratedFlagsFrequency[pol][channel][block] = false;
      }
    }
  }
}

// Flags in Filtered data are in this format: [channels][stations] + time in sparse set
void PreCorrelationFlagger::storeFlagsFrequency(unsigned station, FilteredData* filteredData) {
  for (unsigned channel = 0; channel < itsNrChannels; channel++) {
    for(unsigned block = 0; block < itsNrBlocks; block++) {
      if(itsIntegratedFlagsFrequency[0][channel][block]) {
	unsigned startIndex = block * itsIntegrationFactor;
	filteredData->flags[channel][station].include(startIndex, startIndex+itsIntegrationFactor);
      }
    }
  }
  // Note that the samples themselves are already set to zero.
}


void PreCorrelationFlagger::wipeFlaggedDataFrequency(unsigned station, FilteredData* filteredData) {
  const fcomplex zero = makefcomplex(0.0f, 0.0f);

  for (unsigned channel = 0; channel < itsNrChannels; channel++) {
    for(unsigned block = 0; block < itsNrBlocks; block++) {
      if(itsIntegratedFlagsFrequency[0][channel][block]) {
	unsigned startIndex = block * itsIntegrationFactor;
	for (unsigned time = 0; time < itsIntegrationFactor; time++) {
	  for (unsigned pol = 0; pol < NR_POLARIZATIONS; pol++) {
	    filteredData->samples[channel][station][startIndex + time][pol] = zero;
	  }
	}
      }
    }
  }
}


void PreCorrelationFlagger::integratePowersTime(unsigned station, unsigned pol, FilteredData* filteredData)
{
  for(unsigned time = 0; time < itsNrSamplesPerIntegration; time++) {
    float powerSum = 0.0f;
    unsigned count = 0;
    for (unsigned channel = 0; channel < itsNrChannels; channel++) {
      if(!filteredData->flags[channel][station].test(time)) {
	fcomplex sample = filteredData->samples[channel][station][time][pol];
	powerSum += power(sample);
	count++;
      }
    }

    itsIntegratedPowersTime[pol][time] = powerSum / count;
  }
}


void PreCorrelationFlagger::wipeFlagsTime()
{
  for(unsigned pol=0; pol < NR_POLARIZATIONS; pol++) {
    for (unsigned time = 0; time < itsNrSamplesPerIntegration; time++) {
      itsIntegratedFlagsTime[pol][time] = false;
    }
  }

  // we do not have to look at the samples that are flagged in the frequency direction here, they are already set to zero.
}


void PreCorrelationFlagger::takeUnionOfFlagsTime()
{
  for (unsigned time = 0; time < itsNrSamplesPerIntegration; time++) {
    for(unsigned pol=1; pol < NR_POLARIZATIONS; pol++) {
      itsIntegratedFlagsTime[0][time] = itsIntegratedFlagsTime[0][time] | itsIntegratedFlagsTime[pol][time];
    }
  }
}


void PreCorrelationFlagger::applyFlagsTime(unsigned station, FilteredData* filteredData)
{
  const fcomplex zero = makefcomplex(0.0f, 0.0f);

  for (unsigned time = 0; time < itsNrSamplesPerIntegration; time++) {
    if(itsIntegratedFlagsTime[0][time]) {
      for(unsigned channel = 0; channel < itsNrChannels; channel++) {
	filteredData->flags[channel][station].include(time);
	for(unsigned pol=0; pol < NR_POLARIZATIONS; pol++) {
	    filteredData->samples[channel][station][time][pol] = zero;
	}
      }
    }
  }
}


} // namespace RTCP
} // namespace LOFAR
