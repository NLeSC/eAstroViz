#ifndef LOFAR_CNPROC_FLAGGER_HISTORY_H
#define LOFAR_CNPROC_FLAGGER_HISTORY_H


#define HISTORY_SIZE 256
#define MIN_HISTORY_SIZE 32 // at least 1, and the maximum is HISTORY_SIZE

namespace LOFAR {
namespace RTCP {

class FlaggerHistory {

private:

  unsigned itsSize;
  unsigned itsCurrent;
  float itsSum;

  std::vector<float> itsValues; // [HISTORY_SIZE]

public:

FlaggerHistory() : itsSize(0), itsCurrent(0), itsSum(0.0f) {
    itsValues.resize(HISTORY_SIZE);
    memset(&itsValues[0], 0, HISTORY_SIZE * sizeof(float));
  }


  void add(float val) {
    if (itsSize >= HISTORY_SIZE) { // we are overwriting an old element
      itsSum -= itsValues[itsCurrent];
    } else {
      itsSize++;
    }
    itsSum += val;
    itsValues[itsCurrent] = val;
    itsCurrent++;
    if(itsCurrent >= HISTORY_SIZE) itsCurrent = 0;

#if 0
    std::cout << "HISTORY(" << itsSize << "): ";
    for(int i=0; i<HISTORY_SIZE; i++) {
	    std::cout << itsValues[i] << " ";
    }
    std::cout << std::endl;
#endif
  }


  float getMean() {
    if (itsSize == 0) {
      return 0.0f;
    }
    return itsSum / itsSize;
  }


  float getStdDev() {
    if (itsSize == 0) {
      return 0.0f;
    }

    float stdDev = 0.0f;
    float mean = getMean();

    for (unsigned i = 0; i < itsSize; i++) {
      float diff = itsValues[i] - mean;
      stdDev += diff * diff;
    }
    stdDev /= itsSize;
    stdDev = sqrtf(stdDev);
    return stdDev;
  }


  unsigned getSize() {
    return itsSize;
  }
}; // end of FlaggerHistory
  

} // namespace RTCP
} // namespace LOFAR

#endif // LOFAR_CNPROC_FLAGGER_HISTORY_H
