#include <assert.h>
#include <assert.h>
#include <stdio.h>
#include <limits.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <cuda.h>
#include "cuPrintf.cu"

#include "Data_reader.h"
#include "Device_data.h"
#include "Data_info.h"
#include "Device_array_pointers.h"


#define MAX_SUBBANDS INT_MAX
#define MAX_SEQ_NR   INT_MAX
#define MAX_THREADS  1024
#define NO_THREADS_FREQ   128
#define NO_THREADS_TIME   256
#define DOWNSAMPLE   4
#define TRUE         1
#define FALSE        0
#define MAX_ITERS    7
#define FIRST_THRESHOLD 6.0f
#define BASE_SENSITIVITY 1.0f
#define SIR_VALUE 0.4f


extern "C" {
  void start_timer();
  void stop_timer(float *time);
}


__device__ inline void swap(float & a, float & b){
  float tmp = a;
  a = b;
  b = tmp;
}

__device__ inline void swap(double & a, double & b){
  float tmp = a;
  a = b;
  b = tmp;
}

__device__ float  bitonicSort(float* values, int n, int nr_flagged){
  const int tid = threadIdx.x;


  //Parallel bitonic sort
  for(int k = 2; k <= n; k *= 2){
    //Bitonic merge;
    for(int j = k/2; j>0;j /= 2){
      int ixj = tid ^ j;
      if (ixj > tid){
        if((tid & k) == 0){
          if(values[tid] > values[ixj]){
            swap(values[tid], values[ixj]);
          }
        }else{
          if(values[tid] < values[ixj]){
            swap(values[tid], values[ixj]);
          }
        }
      }
      __syncthreads();
    }
  }
  return values[nr_flagged + (n - nr_flagged)/2];
}

__device__ double bitonicSort(double* values, int n, int nr_flagged){
  const int tid = threadIdx.x;


  //Parallel bitonic sort
  for(int k = 2; k <= n; k *= 2){
    //Bitonic merge;
    for(int j = k/2; j>0;j /= 2){
      int ixj = tid ^ j;
      if (ixj > tid){
        if((tid & k) == 0){
          if(values[tid] > values[ixj]){
            swap(values[tid], values[ixj]);
          }
        }else{
          if(values[tid] < values[ixj]){
            swap(values[tid], values[ixj]);
          }
        }
      }
      __syncthreads();
    }
  }
  return (double)values[nr_flagged + (n - nr_flagged)/2];
}

__device__ float sum_values(float* values){

  unsigned int tid = threadIdx.x;

  for(unsigned int s=blockDim.x/2; s > 32; s>>=1){
    if(tid < s){
      values[tid] += values[tid + s];
    }
    __syncthreads();
  }

  if(tid < 32){
    values[tid] += values[tid + 32];
    values[tid] += values[tid + 16];
    values[tid] += values[tid + 8];
    values[tid] += values[tid + 4];
    values[tid] += values[tid + 2];
    values[tid] += values[tid + 1];
  }

  return values[0];
}

__device__ double sum_values(double* values){

  unsigned int tid = threadIdx.x;

  for(unsigned int s=blockDim.x/2; s > 0; s>>=1){
    if(tid < s){
      values[tid] += values[tid + s];
    }
    __syncthreads();
  }

  return values[0];
}

__device__ void count_flags(unsigned int* nr_flagged, unsigned char* flags){
  unsigned int tid = threadIdx.x;
  if(flags[tid] == TRUE){
    atomicAdd(nr_flagged, 1);
  }
}


__device__ void sum_threshold(float* values, unsigned char* flags, float median, float stddev, int n){
  int window = 1;
  int tid = threadIdx.x;
  float factor = stddev * BASE_SENSITIVITY;
  float sum;
  int pos;
  float threshold;


  for(int i=0;i<MAX_ITERS;i++){
    threshold = fma((FIRST_THRESHOLD * powf(1.5f, i)/ window),factor, median);
    sum = 0.0f;
    if(tid % window == 0){
      for(pos = tid; pos < tid + window; pos++){
        if(flags[pos] != TRUE){
          sum += values[pos];
        }else{
          sum += threshold;
        }
      }
      if(sum >= window * threshold)
        for(pos = tid; pos < tid + window; pos++){
          flags[pos] = TRUE;
        }
      }
    window *= 2;
  }
}

__device__ void sum_threshold(double* values, unsigned char* flags, float median, float stddev, int n){
  int window = 1;
  int tid = threadIdx.x;
  float factor = stddev * BASE_SENSITIVITY;
  float sum;
  int pos;
  float threshold;


  for(int i=0;i<MAX_ITERS;i++){
    threshold = fma((FIRST_THRESHOLD * powf(1.5f, i)/ window),factor, median);
    sum = 0.0f;
    if(tid % window == 0){
      for(pos = tid; pos < tid + window; pos++){
        if(flags[pos] != TRUE){
          sum += values[pos];
        }else{
          sum += threshold;
        }
      }
      if(sum >= window * threshold)
        for(pos = tid; pos < tid + window; pos++){
          flags[pos] = TRUE;
        }
      }
    window *= 2;
  }
}
	
    
        
__global__ void sir_operator(unsigned char* d_flags, int n){
  unsigned char* flags = &(d_flags[(blockIdx.x * n)]);
  float credit = 0.0f;
  float w;
  float max_credit0;
  for(int i = 0; i < n;i++){
    w = flags[i] ? SIR_VALUE : SIR_VALUE - 1.0f;
    max_credit0 = credit > 0.0f ? credit : 0.0f;
    credit = max_credit0 + w;
    flags[i] = credit >= 0.0f;
  }
  credit = 0;
  for(int i = n-1; i > 0;i--){
    w = flags[i] ? SIR_VALUE : SIR_VALUE - 1.0f;
    max_credit0 = credit > 0.0f ? credit : 0.0f;
    credit = max_credit0 + w;
    flags[i] = credit >= 0.0f | flags[i];
  }
}
      
  
// This method reduces all channels in all subbands in a data set to single 
// values. It stores this on the device memory so it can later be flagged.
// It groups values belonging to the same polarization together for 
// easier processing.

__global__ void Reduce_freq(float* values, int nr_blocks, float* results, Device_data* d_data){

  extern __shared__ float shared[];
  shared[threadIdx.x] = values[(blockIdx.x * blockDim.x) + threadIdx.x];
  int pol = blockIdx.x % d_data->nr_polarizations;
  int pos = blockIdx.x / d_data->nr_polarizations;
  int offset = pol * d_data->nr_subbands;
  results[pos + offset] = sum_values(shared);
}

  

// Replace top and bottom 10% with value at border. 
// As the flagged values
// are in front and the first nr_flagged threads don't
// participate, they are not taken into concideration for 
// the top and bottom 10 percentiles.

__device__ void winsorize(float* shared, int nr_flagged, int n){
    if(threadIdx.x < (0.1f * (n - nr_flagged) + nr_flagged)){ 
      shared[threadIdx.x] = shared[(int)(0.1f * (n - nr_flagged) + nr_flagged)];
    }
    if(threadIdx.x > (0.9f * (n - nr_flagged) + nr_flagged)){
      shared[threadIdx.x] = shared[(int)(0.9f * (n - nr_flagged) + nr_flagged)];
    }
}

__device__ void winsorize(double* shared, int nr_flagged, int n){
    if(threadIdx.x < (0.1f * (n - nr_flagged) + nr_flagged)){ 
      shared[threadIdx.x] = shared[(int)(0.1f * (n - nr_flagged) + nr_flagged)];
    }
    if(threadIdx.x > (0.9f * (n - nr_flagged) + nr_flagged)){
      shared[threadIdx.x] = shared[(int)(0.9f * (n - nr_flagged) + nr_flagged)];
    }
}

__device__ __inline__ float get_value(int time, int subband, int pol, int channel, float*
                          values, Device_data* d_data ){
  return values[(time * d_data->nr_subbands * d_data->nr_polarizations * d_data->nr_channels) +
                (subband * d_data->nr_polarizations * d_data->nr_channels) + 
                (pol * d_data->nr_channels) + 
                channel];
}

__device__ __inline__ unsigned char get_value(int time, int subband, int pol, int channel,
                                   unsigned char* values, Device_data* d_data ){
  return values[(time * d_data->nr_subbands * d_data->nr_polarizations * d_data->nr_channels) +
                (subband * d_data->nr_polarizations * d_data->nr_channels) + 
                (pol * d_data->nr_channels) + 
                channel];
}

__device__ __inline__ void set_value(unsigned char value, int time, int subband, int pol, int channel, 
                          unsigned char* values, Device_data* d_data ){
  values[(time * d_data->nr_subbands * d_data->nr_polarizations * d_data->nr_channels) +
         (subband * d_data->nr_polarizations * d_data->nr_channels) + 
         (pol * d_data->nr_channels) + 
         channel] = (value | get_value(time, subband, pol, channel,
                                      values, d_data));
}

__global__ void Reduce_time(float* values, int nr_blocks, int nr_threads, float* results, Device_data* d_data){
  extern __shared__ float shared[];

  int channel = blockIdx.x % d_data->nr_channels;
  int pol = blockIdx.x / d_data->nr_channels % d_data->nr_polarizations;
  int subband = blockIdx.x / (d_data->nr_channels * d_data->nr_polarizations) % d_data->nr_subbands;
  int time = (blockIdx.x / (d_data->nr_channels * d_data->nr_polarizations *
            d_data->nr_subbands)) * nr_threads + threadIdx.x;

  shared[threadIdx.x] = get_value(time, subband, pol, channel, values, d_data);
  results[blockIdx.x] = sum_values(shared);
}
  

__global__ void Flagger_freq(float* values, unsigned char* d_flags, unsigned int n,
    unsigned int* d_nr_flagged){
  
  extern __shared__ float shared[];
  unsigned char* flags;
  unsigned int tid = threadIdx.x;
  float median;
  float stddev;
  int i;
  
  // Copy input to shared memory
  shared[tid] = values[(blockIdx.x * blockDim.x) + tid];
  flags = (unsigned char*) &shared[n];
  flags[tid] = (unsigned char)0;

  __syncthreads();

  for(i=0; i < 2; i++){
    int nr_flagged = d_nr_flagged[blockIdx.x];

    // the sort method will move all flagged values,
    // which have been set to zero, to the front of 
    // the array.
    median = bitonicSort(shared, n, nr_flagged);

    if(tid >= nr_flagged){
      winsorize(shared, nr_flagged, n);
    }
    __syncthreads();

    // Calculate the sum of all values
    float sum = sum_values(shared);

    // Reset values as reduce alters them, and set flagged values 
    // to zero.
    shared[tid] = values[(blockIdx.x * blockDim.x) + tid];
    if(flags[tid]){
      shared[tid] = 0.0f;
    }
    __syncthreads();

    // And sort them again
    bitonicSort(shared, n, nr_flagged);
    
    if(tid >= nr_flagged){
      winsorize(shared, nr_flagged, n);
      // Square the values
      shared[tid] *= shared[tid];
    }
    __syncthreads();

    // Calculate the sum of squares
    float squaredSum = sum_values(shared);

    stddev = sqrtf(squaredSum/n - (sum/n * sum/n));

    // Reset values
    shared[tid] = values[(blockIdx.x * blockDim.x) + tid];
    if(flags[tid]){
      shared[tid] = 0.0f;
    }
    __syncthreads();

    sum_threshold(shared, flags, median, stddev, n); 

    // Reset and recount the number of flags
    d_nr_flagged[blockIdx.x] = 0;
    count_flags(&(d_nr_flagged[blockIdx.x]), flags);
  }

  d_flags[(blockIdx.x * blockDim.x) + tid] = (d_flags[(blockIdx.x *
    blockDim.x)+ tid] | flags[tid]);
}

__global__ void Flagger_time(float* values, unsigned char* d_flags, unsigned int n,
    unsigned int m, unsigned int* d_nr_flagged, Device_data* d_data){

  extern __shared__ float shared[];
  unsigned char* flags;
  unsigned int tid = threadIdx.x;
  float median;
  float stddev;
  int i;
  
  // Copy input to shared memory
  // M is the number of blocks. This is not coalesced
  // memory access, but the data structure leaves us 
  // no other choice.

  int channel = blockIdx.x % d_data->nr_channels;
  int pol = blockIdx.x / d_data->nr_channels % d_data->nr_polarizations;
  int subband = blockIdx.x / (d_data->nr_channels * d_data->nr_polarizations) % d_data->nr_subbands;
  int time = (blockIdx.x / (d_data->nr_channels * d_data->nr_polarizations *
            d_data->nr_subbands)) * n + tid;

  shared[tid] = get_value(time, subband, pol, channel, values, d_data);

  flags = (unsigned char*) &shared[n];
  flags[tid] = get_value(time, subband, pol, channel, d_flags, d_data);

  d_nr_flagged[blockIdx.x] = 0;
  count_flags(&(d_nr_flagged[blockIdx.x]), flags);

  __syncthreads();

  for(i=0; i < 2; i++){
    int nr_flagged = d_nr_flagged[blockIdx.x];

    // the sort method will move all flagged values,
    // which have been set to zero, to the front of 
    // the array.
    median = bitonicSort(shared, n, nr_flagged);

    if(tid >= nr_flagged){
      winsorize(shared, nr_flagged, n);  
    }
    __syncthreads();

    float sum = sum_values(shared);

    // Reset values as reduce alters them, and set flagged values 
    // to zero.
    shared[tid] = get_value(time, subband, pol, channel, values, d_data);
    if(flags[tid]){
      shared[tid] = 0.0f;
    }
    __syncthreads();
    // And sort them again
    bitonicSort(shared, n, nr_flagged);
    
    if(tid >= nr_flagged){
      winsorize(shared, nr_flagged, n);  
      // Square the values
      shared[tid] *= shared[tid];
    }
    __syncthreads();

    float squaredSum = sum_values(shared);

    stddev = sqrtf(squaredSum/n - (sum/n * sum/n));

    // Reset values
    shared[tid] = get_value(time, subband, pol, channel, values, d_data);
    if(flags[tid]){
      shared[tid] = 0.0f;
    }
    __syncthreads();

    sum_threshold(shared, flags, median, stddev, n); 

    // Reset and recount the number of flags
    d_nr_flagged[blockIdx.x] = 0;
    count_flags(&(d_nr_flagged[blockIdx.x]), flags);
  }


  set_value(flags[tid], time, subband, pol, channel, d_flags, d_data);
}

__global__ void Flagger_time_reduced(float* values, unsigned char* d_flags, unsigned int n,
    unsigned int m, unsigned int* d_nr_flagged, Device_data* d_data){
  extern __shared__ double d_shared[];
  unsigned char* flags;
  unsigned int tid = threadIdx.x;
  float median;
  float stddev;
  int i;
  
  // Copy input to shared memory
  int channel = blockIdx.x % d_data->nr_channels;
  int pol = blockIdx.x / d_data->nr_channels % d_data->nr_polarizations;
  int subband = blockIdx.x / (d_data->nr_channels * d_data->nr_polarizations) % d_data->nr_subbands;
  int time = (blockIdx.x / (d_data->nr_channels * d_data->nr_polarizations *
            d_data->nr_subbands)) * n + tid;

  d_shared[tid] = (double)get_value(time, subband, pol, channel, values, d_data);
  flags = (unsigned char*) &d_shared[n];
  flags[tid] = (unsigned char)0;
  /*
  flags[tid] = get_value(time, subband, pol, channel, d_flags, d_data);

  d_nr_flagged[blockIdx.x] = 0;
  count_flags(&(d_nr_flagged[blockIdx.x]), flags);

  if(tid == 0){
    cuPrintf("block: %d, nr_flagged: %d\n", blockIdx.x,
    d_nr_flagged[blockIdx.x]);
  }
  */

  __syncthreads();

  for(i=0; i < 2; i++){
    int nr_flagged = d_nr_flagged[blockIdx.x];

    // the sort method will move all flagged values,
    // which have been set to zero, to the front of 
    // the array.
    median = bitonicSort(d_shared, n, nr_flagged);

    if(tid >= nr_flagged){
      winsorize(d_shared, nr_flagged, n);
    }
    __syncthreads();

    double sum = sum_values(d_shared);

    // Reset values as reduce alters them, and set flagged values 
    // to zero.
    d_shared[tid] = (double)get_value(time, subband, pol, channel, values, d_data);
    if(flags[tid]){
      d_shared[tid] = 0.0f;
    }
    __syncthreads();
    // And sort them again
    bitonicSort(d_shared, n, nr_flagged);
    
    if(tid >= nr_flagged){
      winsorize(d_shared, nr_flagged, n);
      // Square the values
      d_shared[tid] = d_shared[tid] * d_shared[tid];
    }

    __syncthreads();
    double squaredSum = sum_values(d_shared);

    stddev = sqrtf(squaredSum/n - (sum/n * sum/n));

    // Reset values
    d_shared[tid] = (double)get_value(time, subband, pol, channel, values, d_data);
    if(flags[tid]){
      d_shared[tid] = 0.0f;
    }
    __syncthreads();

    sum_threshold(d_shared, flags, median, stddev, n); 

    // Reset and recount the number of flags
    d_nr_flagged[blockIdx.x] = 0;
    count_flags(&(d_nr_flagged[blockIdx.x]), flags);
  }

  if(flags[tid]){
    for(i=0;i<DOWNSAMPLE;i++){
      set_value((unsigned char)1, (time * DOWNSAMPLE) + i , subband, pol,
      channel, d_flags, d_data);
    }
  }
}
__global__ void Flagger_freq_reduced(float* values, unsigned char* d_flags, unsigned int n,
    unsigned int m, unsigned int* d_nr_flagged, Device_data* d_data){
  extern __shared__ double d_shared[];
  unsigned char* flags;
  unsigned int tid = threadIdx.x;
  float median;
  float stddev;
  int i;
  
  // Copy input to shared memory
  d_shared[tid] = (double)values[(blockIdx.x * blockDim.x) + tid];
  flags = (unsigned char*) &d_shared[n];
  flags[tid] = (unsigned char)0;


  __syncthreads();

  for(i=0; i < 2; i++){
    int nr_flagged = d_nr_flagged[blockIdx.x];

    // the sort method will move all flagged values,
    // which have been set to zero, to the front of 
    // the array.
    median = bitonicSort(d_shared, n, nr_flagged);

    if(tid >= nr_flagged){
      winsorize(d_shared, nr_flagged, n);
    }
    __syncthreads();

    double sum = sum_values(d_shared);

    // Reset values as reduce alters them, and set flagged values 
    // to zero.
    d_shared[tid] = values[(blockIdx.x * blockDim.x) + tid];
    if(flags[tid]){
      d_shared[tid] = 0.0f;
    }
    __syncthreads();
    // And sort them again
    bitonicSort(d_shared, n, nr_flagged);
    
    if(tid >= nr_flagged){
      winsorize(d_shared, nr_flagged, n);
      // Square the values
      d_shared[tid] = d_shared[tid] * d_shared[tid];
    }

    __syncthreads();
    double squaredSum = sum_values(d_shared);

    stddev = sqrtf(squaredSum/n - (sum/n * sum/n));

    // Reset values
    d_shared[tid] = values[(blockIdx.x * blockDim.x) + tid];
    if(flags[tid]){
      d_shared[tid] = 0.0f;
    }
    __syncthreads();

    sum_threshold(d_shared, flags, median, stddev, n); 

    // Reset and recount the number of flags
    d_nr_flagged[blockIdx.x] = 0;
    count_flags(&(d_nr_flagged[blockIdx.x]), flags);
  }

  int pol = blockIdx.x % d_data->nr_polarizations;
  int timeslot = (blockIdx.x/d_data->nr_polarizations) * (d_data->nr_subbands * d_data->nr_polarizations * d_data->nr_channels);
  int subband = (threadIdx.x * d_data->nr_polarizations * d_data->nr_channels) + (pol * d_data->nr_channels);

  if(flags[tid]){
    for(i=0;i<d_data->nr_channels;i++){
      d_flags[timeslot + subband + i] = (unsigned char)1;
    }
  }
}

void merge_flagmap(unsigned char* flagmap, int n, int pols, int nr_channels){
  int i,j,k;
  // You cannot merge a flagmap with only one polarisation
  if(pols == 1){
    return;
  }

  for(i=0;i<n/pols;i++){
    for(j=0;j<pols;j++){
      // The next polarisation is always `nr_channels` values away
      if(flagmap[n + j * nr_channels]){
    	for(k=0;k<pols;k++){
	  flagmap[n + k * nr_channels] = (unsigned char) 1;
        }
      }
    }
  }
}
   

void get_and_check_arguments(int argc, char** argv, int* direction, int* reduce, int* little_endian, int* sir){
  int i;

  if(argc > 6 || argc < 2){
    printf("Usage: flagger <filename> [--little-endian] [-f | -t | -b] [-r] [-sir]\n");
    exit(1);
  }

  if(argc > 2){
    for(i=2;i<argc;i++){
      if (strcmp(argv[i], "-f") == 0){
        *direction = 0;
        continue;
      }
      if (strcmp(argv[i], "-t") == 0){
        *direction = 1;
        continue;
      }
      if (strcmp(argv[i], "-b") == 0){
        *direction = 2;
        continue;
      }
      if (strcmp(argv[i], "-b2") == 0){
        *direction = 3;
        continue;
      }
      if (strcmp(argv[i], "-r") == 0){
        *reduce = 1;
        continue;
      }
      if (strcmp(argv[i], "--little-endian") == 0){
        *little_endian = 1;
        continue;
      }
      if (strcmp(argv[i], "-sir") == 0){
        *sir = 1;
        continue;
      }else{
        fprintf(stderr, "unrecognized option %s.\n", argv[i]);
        exit(1);
      }
    }
  }
}

void allocate_and_zero_memory(unsigned char** d_flags, unsigned int** d_nr_flagged, int data_size, int nr_blocks){
  cudaError_t devRetVal;

  devRetVal = cudaMalloc(&(*d_flags), (size_t)(data_size *  sizeof(unsigned char)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error allocating memory for flags\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }

  devRetVal = cudaMalloc(&(*d_nr_flagged), (size_t)(nr_blocks *  sizeof(unsigned int)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error allocating memory for n's\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }

  devRetVal = cudaMemset(*d_nr_flagged, 0, (size_t)(nr_blocks *  sizeof(unsigned int)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error setting initial n values\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }

  devRetVal = cudaMemset(*d_flags, 0, (size_t)(data_size * sizeof(unsigned
  char)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error zero'ing flagmap\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }
}

void allocate_nr_flagged(unsigned int** d_nr_flagged, int nr_blocks){
  cudaError_t devRetVal;


  devRetVal = cudaMalloc(&(*d_nr_flagged), (size_t)(nr_blocks *  sizeof(unsigned int)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error allocating memory for n's\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }
}


unsigned char* frequency_flagger(int data_size, unsigned char* d_flags, Data_info* h_data_info, Device_array_pointers* ptrs){
  int nr_blocks;
  int shared_mem_size; 
  int nr_threads;
  cudaError_t devRetVal;
  unsigned int *d_nr_flagged = NULL;

  nr_threads = h_data_info->nr_channels;
  shared_mem_size = nr_threads * sizeof(float) + 
                    nr_threads * sizeof(unsigned char);
  nr_blocks = h_data_info->nr_times * h_data_info->nr_subbands *
              h_data_info->nr_polarizations;

  allocate_and_zero_memory(&d_flags, &d_nr_flagged, data_size, nr_blocks);

  Flagger_freq<<<nr_blocks, nr_threads, shared_mem_size>>>
    (ptrs->data, d_flags, nr_threads, d_nr_flagged);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }

  return d_flags;
}

unsigned char* time_flagger(int data_size, unsigned char* d_flags, 
                            Data_info* h_data_info, Device_array_pointers* ptrs,
                            Device_data* d_data, int chain){
  int nr_blocks;
  int shared_mem_size; 
  int nr_threads;
  cudaError_t devRetVal;
  unsigned int *d_nr_flagged = NULL;

  nr_threads = NO_THREADS_TIME;
  shared_mem_size = nr_threads * sizeof(float) + 
                    nr_threads * sizeof(unsigned char);
  nr_blocks = h_data_info->nr_channels * h_data_info->nr_polarizations *
              h_data_info->nr_subbands * (h_data_info->nr_times / nr_threads);

  if(!chain){
    allocate_and_zero_memory(&d_flags, &d_nr_flagged, data_size, nr_blocks);
  }else{
    allocate_nr_flagged(&d_nr_flagged, nr_blocks);
  }

  Flagger_time<<<nr_blocks, nr_threads, shared_mem_size>>>
    (ptrs->data, d_flags, nr_threads, nr_blocks, d_nr_flagged, d_data);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }

  return d_flags;
}

float* reduce_frequency(int data_size, Data_info* h_data_info, Device_array_pointers* ptrs, Device_data* d_data){
  cudaError_t devRetVal;
  int nr_blocks;
  int nr_threads;
  int shared_mem_size;
  float* d_values_reduced;

  nr_threads = h_data_info->nr_channels;
  nr_blocks = data_size/nr_threads;
  shared_mem_size = nr_threads * sizeof(float);

  devRetVal = cudaMalloc(&d_values_reduced, (size_t)((data_size /
  h_data_info->nr_channels) * sizeof(float)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error allocating memory for reduced values\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }

  Reduce_freq<<<nr_blocks, nr_threads, shared_mem_size>>>
    (ptrs->data, data_size/h_data_info->nr_channels, d_values_reduced, d_data);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }

  return d_values_reduced;
}

float* reduce_time(int data_size, Data_info* h_data_info, Device_array_pointers* ptrs, Device_data* d_data){
  cudaError_t devRetVal;
  int nr_blocks;
  int nr_threads;
  int shared_mem_size;
  float* d_values_reduced;

  nr_threads = DOWNSAMPLE;
  nr_blocks = h_data_info->nr_channels * h_data_info->nr_polarizations *
              h_data_info->nr_subbands * (h_data_info->nr_times / nr_threads);
  shared_mem_size = nr_threads * sizeof(float);

  devRetVal = cudaMalloc(&d_values_reduced, (size_t)((data_size /
  DOWNSAMPLE) * sizeof(float)));
  if(devRetVal != cudaSuccess){
    fprintf(stderr, "Error allocating memory for reduced values\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }

  Reduce_time<<<nr_blocks, nr_threads, shared_mem_size>>>
    (ptrs->data, nr_blocks, nr_threads, d_values_reduced, d_data);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }

  return d_values_reduced;
}


unsigned char* frequency_flagger_reduced(int data_size, unsigned char* d_flags, 
                               Data_info* h_data_info, 
                               Device_array_pointers* ptrs, 
                               Device_data* d_data){
  int nr_blocks;
  int shared_mem_size; 
  int nr_threads;
  cudaError_t devRetVal;
  unsigned int *d_nr_flagged = NULL;
  float* d_values_reduced = NULL;

  d_values_reduced = reduce_frequency(data_size, h_data_info, ptrs, d_data);

  nr_threads = h_data_info->nr_subbands;
  nr_blocks = h_data_info->nr_times * h_data_info->nr_polarizations;
  shared_mem_size = sizeof(double) * nr_threads + 
                    sizeof(unsigned char) * nr_threads;

  allocate_and_zero_memory(&d_flags, &d_nr_flagged, data_size, nr_blocks);

  Flagger_freq_reduced<<<nr_blocks, nr_threads, shared_mem_size>>>
    (d_values_reduced, d_flags, nr_threads, nr_blocks, d_nr_flagged, d_data);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }

  return d_flags;
}

unsigned char* time_flagger_reduced(int data_size, unsigned char* d_flags, 
                               Data_info* h_data_info, 
                               Device_array_pointers* ptrs, 
                               Device_data* d_data,
                               int chain){
  int nr_blocks;
  int shared_mem_size; 
  int nr_threads;
  cudaError_t devRetVal;
  unsigned int *d_nr_flagged = NULL;
  float* d_values_reduced = NULL;

  d_values_reduced = reduce_time(data_size, h_data_info, ptrs, d_data);

  nr_threads = NO_THREADS_FREQ;
  nr_blocks = h_data_info->nr_channels * h_data_info->nr_polarizations *
  h_data_info->nr_subbands * (h_data_info->nr_times / DOWNSAMPLE / nr_threads);
  shared_mem_size = sizeof(double) * nr_threads + 
                    sizeof(unsigned char) * nr_threads;

  if(!chain){
    allocate_and_zero_memory(&d_flags, &d_nr_flagged, data_size, nr_blocks);
  }else{
    allocate_nr_flagged(&d_nr_flagged, nr_blocks);
  }


  Flagger_time_reduced<<<nr_blocks, nr_threads, shared_mem_size>>>
    (d_values_reduced, d_flags, nr_threads, nr_blocks, d_nr_flagged, d_data);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }

  return d_flags;
}


int main(int argc, char** argv){
  Data_info* h_data_info = NULL;
  Device_data* d_data = NULL;
  Device_array_pointers* ptrs = NULL;
  float time;
  int i,j;
  int nr_blocks;
  unsigned int *d_nr_flagged;
  int data_size;
  unsigned char* d_flags = NULL;
  unsigned char* h_flags;
  float * h_values;
  cudaError_t devRetVal;
  int* meta_data = NULL;
  int little_endian = 0;
  int direction = 0;
  int reduce = 0;
  int sir = 0;

  get_and_check_arguments(argc, argv, &direction, &reduce, &little_endian, &sir);


  /*
  int a = 50 * 1024;
  int b = 32;
  int c = 256;
  int d = 1;
  h_data_info = fake_data(a,b,c,d);
  */

  //printf("analyzing %d samples\n", a * b * c *d);

  // Raw files are stored in little endian, and do not contain
  // the necessary meta-data. That's why it's hard-coded here
  if(little_endian){
    meta_data = (int*)malloc(5 * sizeof(int));
    meta_data[0] = 1;   //stations
    meta_data[1] = 59 * 762;  //seconds
    meta_data[2] = 32;  //subbands
    meta_data[3] = 256; //channels
    meta_data[4] = 1;   //polarizations
  }


  h_data_info = read_file(argv[1], meta_data, little_endian);

  ptrs = malloc_cuda_memory(h_data_info, &d_data);

  data_size = h_data_info->nr_times * h_data_info->nr_subbands *
              h_data_info->nr_polarizations * h_data_info->nr_channels;


  start_timer();
  cudaPrintfInit();

  switch(direction){
    case 0:
      if(reduce){
        d_flags = frequency_flagger_reduced(data_size, d_flags, h_data_info, ptrs, d_data); 
      }else{
        d_flags = frequency_flagger(data_size, d_flags, h_data_info, ptrs);
      }
      break;
    case 1:
      if(reduce){
        d_flags = time_flagger_reduced(data_size, d_flags, h_data_info, ptrs,
                                       d_data, FALSE); 
      }else{
        d_flags = time_flagger(data_size, d_flags, h_data_info, ptrs, d_data,
                               FALSE);
      }
      break;
    case 2:
      if(reduce){
        d_flags = frequency_flagger_reduced(data_size, d_flags, h_data_info, ptrs, d_data); 
        d_flags = time_flagger_reduced(data_size, d_flags, h_data_info, ptrs,
                                       d_data, TRUE); 
      }else{
        d_flags = frequency_flagger(data_size, d_flags, h_data_info, ptrs);
        d_flags = time_flagger(data_size, d_flags, h_data_info, ptrs, d_data,
                               TRUE);
      }
      break;
    case 3:
      if(reduce){
        d_flags = time_flagger_reduced(data_size, d_flags, h_data_info, ptrs,
                                       d_data, TRUE); 
        d_flags = frequency_flagger_reduced(data_size, d_flags, h_data_info, ptrs, d_data); 
      }else{
        d_flags = time_flagger(data_size, d_flags, h_data_info, ptrs, d_data,
                               TRUE);
        d_flags = frequency_flagger(data_size, d_flags, h_data_info, ptrs);
      }
      break;
  }

  if(sir){
    nr_blocks = h_data_info->nr_times * h_data_info->nr_subbands * h_data_info->nr_polarizations;
    sir_operator<<<nr_blocks,1>>>(d_flags, h_data_info->nr_channels);
  }


  cudaPrintfDisplay(stdout, true);
  cudaPrintfEnd();
  cudaDeviceSynchronize();

  stop_timer(&time);
  //printf("Kernel ran for %f\t(ms)\n", time);

  if( (devRetVal = cudaGetLastError()) != cudaSuccess){
    fprintf(stderr, "Kernel has some kind of issue:\n%s\n",
        cudaGetErrorString(devRetVal));
    exit(1);
  }
  //printf("calculations done...\n");

  h_flags = (unsigned char *) malloc(sizeof(unsigned char) * data_size);
  if(h_flags == NULL){
    perror("error allocating memory for h_flags:");
    exit(1);
  }

  h_values = (float*) malloc(sizeof(float) * data_size);
  if(h_values == NULL){
    perror("error allocating memory for h_values:");
    exit(1);
  }

  // Copy flags from device back to host
  if( (devRetVal = cudaMemcpy(h_flags, d_flags,
          sizeof(unsigned char) * data_size, 
          cudaMemcpyDeviceToHost))
        != cudaSuccess){
    fprintf(stderr, "Error copying flags from device to host\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }

  // Copy values from device to host
  if( (devRetVal = cudaMemcpy(h_values, ptrs->data,
          sizeof(float) * data_size, 
          cudaMemcpyDeviceToHost))
        != cudaSuccess){
    fprintf(stderr, "Error copying data from device to host\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(devRetVal));
    exit(1);
  }


  cudaDeviceReset();

  cudaFree(d_flags);
  cudaFree(d_nr_flagged);



  merge_flagmap(h_flags, data_size, h_data_info->nr_polarizations, h_data_info->nr_channels);
  for(i=0;i<data_size;i++){
    if(h_flags[i]){
	    h_values[i] = 0.0f;
    }
  }

  //printf("%f\n", h_values[0]);
  // Print resulting flagmap
  /*
  int n = h_data_info->nr_channels;
  for(i=0;i< data_size/n ;i += h_data_info->nr_polarizations){
    for(j=0;j<n;j++){
      unsigned char flag = (unsigned char) (h_flags[(i*n) + j] | h_flags[(i*n) + n + j]);
      printf("%u",(unsigned int)flag);
    }
    printf("\n");
  }
  */

  
  fwrite(h_values, data_size, sizeof(float), stdout);


  return 0;
}

