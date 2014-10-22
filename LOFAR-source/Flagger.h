#ifndef LOFAR_CNPROC_FLAGGER_H
#define LOFAR_CNPROC_FLAGGER_H

#include <Interface/MultiDimArray.h>
#include <Common/lofar_complex.h>

#include <FlaggerHistory.h>

#include <string>
#include <vector>

namespace LOFAR {
namespace RTCP {

class Parset;

enum FlaggerType {
  FLAGGER_THRESHOLD,
  FLAGGER_SUM_THRESHOLD
};

enum FlaggerStatisticsType {
  FLAGGER_STATISTICS_NORMAL,
  FLAGGER_STATISTICS_WINSORIZED
};


/*
  All flagging methods return the number of *additional* samples that they flagged.
 */
class Flagger {

public:

  // The firstThreshold of 6.0 is taken from Andre's code.
  Flagger(const Parset& parset, const unsigned nrStations,   const unsigned nrSubbands, const unsigned nrChannels, const float cutoffThreshold = 6.0f, 
	  float baseSentitivity = 1.0f, FlaggerType flaggerType = FLAGGER_SUM_THRESHOLD, FlaggerStatisticsType flaggerStatisticsType = FLAGGER_STATISTICS_WINSORIZED);

private:
  float evaluateGaussian(const float x, const float sigma);
  float logBase2(const float x);
  void oneDimensionalConvolution(const float* data, const unsigned dataSize, float* dest, const float* kernel, const unsigned kernelSize);
  unsigned sumThreshold2DHorizontal(MultiDimArray<float,2> &powers, MultiDimArray<bool,2> &flags, const unsigned window, const float threshold);
  unsigned sumThreshold2DVertical(MultiDimArray<float,2> &powers, MultiDimArray<bool,2> &flags, const unsigned window, const float threshold);
  void apply1DflagsTo2D(MultiDimArray<bool,2> &flags, std::vector<bool> & integratedFlags);

  // private version to avoid code duplication. Private, because it changes data!
  void calculateWinsorizedMean(std::vector<float>& powers, float& mean);

protected:

  // Does simple thresholding.
  unsigned thresholdingFlagger1D(std::vector<float>& powers, std::vector<bool>& flags);
  unsigned thresholdingFlagger2D(const MultiDimArray<float,2> &powers, MultiDimArray<bool,2> &flags);

  // Does sum thresholding.
  unsigned sumThresholdFlagger1D(std::vector<float>& powers, std::vector<bool>& flags, const float sensitivity);

  unsigned sumThresholdFlagger1DWithHistory(std::vector<float>& powers, std::vector<bool>& flags, const float sensitivity, FlaggerHistory& history);

  unsigned sumThresholdFlagger2D(MultiDimArray<float,2> &powers, MultiDimArray<bool,2> &flags, const float sensitivity);

  unsigned sumThresholdFlagger2DWithHistory(MultiDimArray<float,2> &powers, MultiDimArray<bool,2> &flags, std::vector<float> &integratedPowers,
					std::vector<bool> &integratedFlags, const float sensitivity, 
					MultiDimArray<FlaggerHistory, 3>& history, unsigned station, unsigned subband, unsigned pol);


  // Does sum thresholding on samples, a gaussion smooth, calculates difference, and flags again.
  unsigned sumThresholdFlagger1DSmoothed(std::vector<float>& powers, std::vector<float>& smoothedPowers, 
					 std::vector<float>& powerDiffs, std::vector<bool>& flags, const float sensitivity);

  unsigned sumThresholdFlagger1DSmoothedWithHistory(std::vector<float>& powers, std::vector<float>& smoothedPowers, 
						std::vector<float>& powerDiffs, 
						std::vector<bool>& flags, FlaggerHistory& history, const float sensitivity);

  void calculateMean(const std::vector<float>& powers, float& mean);
  void calculateMeanAndStdDev(const std::vector<float>& powers, float& mean, float& stdDev);
  unsigned calculateMedian(const std::vector<float>& powers, float& median); // calculate median, return position of the element
  unsigned calculateMedian(const std::vector<float>& powers, const std::vector<bool>& flags, float& median); // calculate median, return position of the element, or -1 if all was flagged

  void calculateMean(const std::vector<float>& powers, const std::vector<bool>& flags, float& mean);
  void calculateMeanAndStdDev(const std::vector<float>& powers, const std::vector<bool>& flags, float& mean, float& stdDev);
  void calculateNormalStatistics(const std::vector<float>& powers, const std::vector<bool>& flags, float& mean, float& median, float& stdDev);
  void calculateWinsorizedStatistics(const std::vector<float>& powers, const std::vector<bool>& flags, float& mean, float& median, float& stdDev);
  void calculateWinsorizedMean(const std::vector<float>& powers, const std::vector<bool>& flags, float& mean);
  void calculateWinsorizedMean(const MultiDimArray<float,2>& powers, const MultiDimArray<bool,2>& flags, float& mean);
  void calculateStatistics(const std::vector<float>& powers, const std::vector<bool>& flags, float& mean, float& median, float& stdDev);
  void calculateStatistics(const MultiDimArray<float,2> &powers, MultiDimArray<bool,2> &flags, float& mean, float& median, float& stdDev);

  float calcThresholdI(float threshold1, unsigned window, float p);
  unsigned sumThreshold1D(std::vector<float>& powers, std::vector<bool>& flags, const unsigned window, const float threshold);
  void oneDimensionalGausConvolution(const float* data, const unsigned dataSize, float*dest, const float sigma);

  bool addToHistory(const float value, FlaggerHistory& history, float threshold = 7.0f);

  // Note that the two SIR functions return the total number of flagged samples. 
  unsigned SIROperator(std::vector<bool>& flags, float eta);
  unsigned SIROperator2D(MultiDimArray<bool,2>& flags, float eta);

  unsigned countFlaggedSamples(const std::vector<bool>& flags);
  unsigned countFlaggedSamples(const MultiDimArray<bool,2> &flags);

  FlaggerType getFlaggerType(std::string t);
  std::string getFlaggerTypeString(FlaggerType t);
  std::string getFlaggerTypeString();

  std::string getFlaggerStatisticsTypeString();
  std::string getFlaggerStatisticsTypeString(FlaggerStatisticsType t);
  FlaggerStatisticsType getFlaggerStatisticsType(std::string t);

  float power(fcomplex in);

  const Parset& itsParset;
  const unsigned itsNrStations;
  const unsigned itsNrSubbands;
  const unsigned itsNrChannels;
  const float itsCutoffThreshold;
  const float itsBaseSensitivity;
  const FlaggerType itsFlaggerType;
  const FlaggerStatisticsType itsFlaggerStatisticsType;
};

inline float Flagger::power(fcomplex in) {
  return real(in) * real(in) + imag(in) * imag(in);
}

} // namespace RTCP
} // namespace LOFAR

#endif // LOFAR_CNPROC_FLAGGER_H
