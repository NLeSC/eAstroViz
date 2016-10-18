#include <stdio.h>
#include <time.h>
#include <limits.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <float.h>
#include "Data_reader.h"
#include "Device_data.h"
#include "Data_info.h"


#define MAX_SUBBANDS INT_MAX
#define MAX_SEQ_NR   INT_MAX
#define TRUE         1
#define FALSE        0


/* Converts four big endian ordered bytes into a float */
float bytes2floatBE(unsigned char *raw){
  int i;
  union{
    unsigned char bytes[4];
    float fp;
  } un ;
  for(i=0;i<4;i++){
    un.bytes[i] = raw[3-i];
  }
  return un.fp;
}

/* Converts four little endian ordered bytes into a float */
float bytes2floatLE(unsigned char *raw){
  int i;
  union{
    unsigned char bytes[4];
    float fp;
  } un ;
  for(i=0;i<4;i++){
    un.bytes[i] = raw[i];
  }
  return un.fp;
}

void remove_initial_flagged(Data_info* h_data_info){
  long initial_flagged_count = 0;
  int time;
  int subband;
  int channel;
  int pol;

  for(time = 0;time < h_data_info->nr_times; time++){
    for(subband = 0; subband < h_data_info->nr_subbands; subband++){
      for(channel = 0; channel < h_data_info->nr_channels; channel++){
        for(pol = 0; pol < h_data_info->nr_polarizations; pol++){
          if(h_data_info->initial_flagged[time][subband][channel]){
            h_data_info->data[time][subband][pol][channel] = 0.0f;
            initial_flagged_count++;
          } 
        }
      }
    }
  }
}



/* Allocates memory */
void allocate_memory(float***** data, unsigned char**** flagged, 
    unsigned char**** initial_flagged, int nr_times, int nr_subbands, 
    int nr_channels, int nr_polarizations){
  
  int i,j,k;
  *data = (float****) malloc(sizeof(float***) * nr_times);
  if(*data == NULL){
    perror("Error allocating memory for data buffer:");
    exit(1);
  }
  *flagged = (unsigned char***) malloc(sizeof(unsigned char **) * nr_times);
  if(*flagged == NULL){

    perror("Error allocating memory for flag buffer:");
    exit(1);
  }
  *initial_flagged = (unsigned char***) malloc(sizeof(unsigned char **) * nr_times);
  if(*initial_flagged == NULL){
    perror("Error allocating memory for flag buffer:");
    exit(1);
  }

  for(i=0;i<nr_times;i++){
    (*data)[i] = (float ***) malloc(sizeof(float**) * nr_subbands);
    if((*data)[i] == NULL){
      perror("Error allocating memory for data on time");
      exit(1);
    }
    (*flagged)[i] = (unsigned char **) malloc(sizeof(unsigned char *) * nr_subbands);
    if((*flagged)[i] == NULL){
      perror("Error allocating memory for flags on time");
      exit(1);
    }
    (*initial_flagged)[i] = (unsigned char **) malloc(sizeof(unsigned char *) 
    * nr_subbands);
    if((*initial_flagged)[i] == NULL){
      perror("Error allocating memory for initial_flags on time");
      exit(1);
    }

    for(j=0;j<nr_subbands;j++){
      (*data)[i][j] = (float **) malloc(sizeof(float*) * nr_polarizations);
      if((*data)[i][j] == NULL){
        perror("Error allocating memory for data on subband at time");
        exit(1);
      }

      for(k=0;k<nr_polarizations;k++){
        (*data)[i][j][k] = (float *) malloc(sizeof(float) * nr_channels);
        if((*data)[i][j][k] == NULL){
          perror("Error allocating memory for data on on polarization on subband at time ");
          exit(1);
        }
        memset((*data)[i][j][k], 0, sizeof(float) * nr_channels);
      }
      (*flagged)[i][j] = (unsigned char*) malloc(sizeof(unsigned char) * nr_channels);
      if((*flagged)[i][j] == NULL){
        perror("Error allocating memory for flags on subband at time");
        exit(1);
      }
      (*initial_flagged)[i][j] = (unsigned char*) malloc(sizeof(unsigned char) *
      nr_channels);
      if((*initial_flagged)[i][j] == NULL){
        perror("Error allocating memory for initial flags on subband at time");
        exit(1);
      }
      memset((*flagged)[i][j], 0, sizeof(unsigned char) * nr_channels);
      memset((*initial_flagged)[i][j], 0, sizeof(unsigned char) * nr_channels);
    }
  }
}

Data_info* read_file(char* file_name, int* meta_data, int little_endian)
{
  int nr_stations;
  int nr_times;
  int nr_subbands;
  int nr_subbands_in_file;
  int nr_channels;
  int nr_polarizations;
  int integration_factor = 1;
  int station_block_size;
  FILE* fin;
  int second;
  int time;
  int subband;
  int channel;
  int pol;
  unsigned char* byte_buffer;
  float sample;
  int index;
  int err;
  float**** data;
  unsigned char*** flagged;
  unsigned char*** initial_flagged;
  Data_info* data_info;


  /*open the file*/

  fin = fopen(file_name, "r");
  if(fin == NULL){
    perror("Error opening file:");
    exit(1);
  }

  if(meta_data == NULL){

    /* Read the number of stations */
    if((fread(&nr_stations, sizeof(nr_stations), 1, fin)) == 0){
      perror("Error reading number of stations:");
      exit(1);
    }
    /* Convert to little endianness, as file is stored big endian */
    nr_stations = htonl(nr_stations);
    
    /* Read the number of times*/
    if((fread(&nr_times, sizeof(nr_times), 1, fin)) == 0){
      perror("Error reading number of times:");
      exit(1);
    }
    /* Convert to little endianness, as file is stored big endian */
    nr_times = htonl(nr_times);
    nr_times /= integration_factor;

    /* Read the number of subbands */
    if((fread(&nr_subbands_in_file, sizeof(nr_subbands_in_file), 1, fin)) == 0){
      perror("Error reading number of subbands:");
      exit(1);
    }
    /* Convert to little endianness, as file is stored big endian */
    nr_subbands_in_file = htonl(nr_subbands_in_file);

    /* Read the number of channels */
    if((fread(&nr_channels, sizeof(nr_channels), 1, fin)) == 0){
      perror("Error reading number of channels:");
      exit(1);
    }
    /* Convert to little endianness, as file is stored big endian */
    nr_channels = htonl(nr_channels);

    if((fread(&nr_polarizations, sizeof(nr_polarizations), 1, fin)) == 0){
      perror("Error reading number of polarizations:");
      exit(1);
    }
    /* Convert to little endianness, as file is stored big endian */
    nr_polarizations = htonl(nr_polarizations);

  }else{

    nr_stations         = meta_data[0];
    nr_times            = meta_data[1];
    nr_subbands_in_file = meta_data[2];
    nr_channels         = meta_data[3];
    nr_polarizations    = meta_data[4];

  }


  //printf("Parameters read! nr_stations: %d, nr_times: %d, nr_subbands: %d, nr_channels: %d, nr_polarizations: %d\n", nr_stations, nr_times, nr_subbands_in_file, nr_channels, nr_polarizations);

  nr_subbands = nr_subbands_in_file;
  if(MAX_SUBBANDS < nr_subbands_in_file){
    nr_subbands = MAX_SUBBANDS;
  }

  
  /* allocate memory for data and flags. */
  allocate_memory(&data, &flagged, &initial_flagged, nr_times, nr_subbands, nr_channels, nr_polarizations);

  station_block_size = integration_factor * nr_subbands_in_file * nr_channels *
                       nr_polarizations * sizeof(float);


  byte_buffer =  (unsigned char*) malloc(sizeof(unsigned char) * station_block_size);
  if(byte_buffer == NULL){
    perror("Error allocating memory for byte_buffer:");
    exit(1);
  }


  for(second = 0; second < nr_times; second ++){
    if(second > MAX_SEQ_NR){
      break;
    }
    err = fread(byte_buffer, sizeof(unsigned char), station_block_size, fin);
    if(err != station_block_size){
      perror("Error reading bytes into buffer");
      exit(1);
    }
        
    index = 0;
    
    for(time = 0; time < integration_factor; time++){
      for(subband = 0; subband < nr_subbands_in_file; subband++){
        for(channel = 0; channel < nr_channels; channel++){
          for(pol = 0; pol < nr_polarizations; pol++){
            if(little_endian){
              sample = bytes2floatLE(&byte_buffer[index * sizeof(float)]);;
            }else{
              sample = bytes2floatBE(&byte_buffer[index * sizeof(float)]);;
            }
            index++;
            if(subband < MAX_SUBBANDS){
              if (sample < 0.0f){
                initial_flagged[second][subband][channel] = TRUE;
                flagged[second][subband][channel] = TRUE;
              } else {
                data[second][subband][pol][channel] += sample;
              }
            }
          }
        }
      }
    }
  }

  data_info = (Data_info*) malloc(sizeof(Data_info));
  data_info->nr_stations         = nr_stations;
  data_info->nr_times            = nr_times;
  data_info->nr_subbands         = nr_subbands;
  data_info->nr_subbands_in_file = nr_subbands_in_file;
  data_info->nr_channels         = nr_channels;
  data_info->integration_factor  = integration_factor;
  data_info->nr_polarizations    = nr_polarizations;
  data_info->station_block_size  = station_block_size;
  data_info->data                = data;
  data_info->flagged             = flagged;
  data_info->initial_flagged     = initial_flagged;


  remove_initial_flagged(data_info);

  return data_info;
}

Data_info* fake_data(int nr_times, int nr_subbands, int nr_channels, int nr_polarizations){
  Data_info* result = (Data_info*) malloc(sizeof(Data_info));
  float**** data;
  unsigned char*** flagged;
  unsigned char*** initial_flagged;
  int t, subband, channel, pol;
  float sample;

  srand((unsigned int)time(NULL));
  
  allocate_memory(&data, &flagged, &initial_flagged, nr_times, nr_subbands, nr_channels, nr_polarizations);
  for(t = 0; t < nr_times; t++){
    for(subband = 0; subband < nr_subbands; subband++){
      for(channel = 0; channel < nr_channels; channel++){
        for(pol = 0; pol < nr_polarizations; pol++){
          sample = (float)rand()/(float)(RAND_MAX/2493000000.0f) + 7000000.0f;
          data[t][subband][pol][channel] += sample;
        }
      }
    }
  }

  result->nr_times            = nr_times;
  result->nr_subbands         = nr_subbands;
  result->nr_channels         = nr_channels;
  result->nr_polarizations    = nr_polarizations;
  result->data                = data;
  result->flagged             = flagged;
  result->initial_flagged     = initial_flagged;

  return result;
}
  

Device_array_pointers* allocate_cuda_memory(Data_info* h_data_info){
  int nr_times = h_data_info->nr_times;
  int nr_subbands = h_data_info->nr_subbands;
  int nr_channels = h_data_info->nr_channels;
  int nr_polarizations = h_data_info->nr_polarizations;
  int i,j,k,l;
  int index;
  cudaError_t cErr;
  unsigned char* flagged;
  unsigned char* initial_flagged;
  Device_array_pointers* result;


  result = (Device_array_pointers*) malloc(sizeof(Device_array_pointers));
  if(result == NULL){
    perror("Error allocating memory for device array pointers");
    exit(1);
  }

  /* Allocate memory on host */
  float* linear_data = (float *)malloc(sizeof(float) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations));
  if(linear_data == NULL){
    perror("Error allocating memory for linear_data");
    exit(1);
  }

  unsigned char* linear_flagged = (unsigned char*)malloc(sizeof(unsigned char) * (nr_times *
        nr_subbands * nr_channels * nr_polarizations));
  if(linear_flagged == NULL){
    perror("Error allocating memory for linear_flagged");
    exit(1);
  }

  unsigned char* linear_initial_flagged = (unsigned char*)malloc(sizeof(unsigned char) * (nr_times *
        nr_subbands * nr_channels * nr_polarizations));
  if(linear_initial_flagged == NULL){
    perror("Error allocating memory for linear_initial_flagged");
    exit(1);
  }

  /* Allocate memory on device */
  if( (cErr = cudaMalloc(&result->data, sizeof(float) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations))) != cudaSuccess){
   fprintf(stderr, "Error allocating device memory for data.\n");
   fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
   exit(1);
  }

  if( (cErr = cudaMalloc(&flagged, sizeof(unsigned char) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations))) != cudaSuccess){
   fprintf(stderr, "Error allocating device memory for flags.\n");
   fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
   exit(1);
  }

  if( (cErr = cudaMalloc(&initial_flagged, sizeof(unsigned char) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations))) != cudaSuccess){
   fprintf(stderr, "Error allocating device memory for initial flags.\n");
   fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
   exit(1);
  }

  //printf("data size = %d\n", nr_times * nr_subbands * nr_channels *
      //nr_polarizations);


  /* Fill linearized array */
  for(i=0;i<nr_times;i++){
    for(j=0;j<nr_subbands;j++){
      for(k=0;k<nr_polarizations;k++){
        for(l=0;l<nr_channels;l++){
          index = (i * nr_subbands * nr_polarizations * nr_channels) 
            + (j * nr_polarizations * nr_channels) 
            + (k * nr_channels) + l;
          linear_data[index] = h_data_info->data[i][j][k][l];
          linear_flagged[index] = h_data_info->flagged[i][j][k];
          linear_initial_flagged[index] = h_data_info->initial_flagged[i][j][k];
        }
      }
    }
  }


  /* Copy linearized arrays to device */

  if( (cErr = cudaMemcpy(result->data, linear_data, sizeof(float) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations), cudaMemcpyHostToDevice)) !=
        cudaSuccess){
    fprintf(stderr, "Error copying data to device:\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
    exit(1);
  }

  if( (cErr = cudaMemcpy(flagged, linear_flagged, sizeof(unsigned char) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations ), cudaMemcpyHostToDevice)) !=
        cudaSuccess){
    fprintf(stderr, "Error copying flags to device:\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
    exit(1);
  }

  if( (cErr = cudaMemcpy(initial_flagged, linear_initial_flagged, sizeof(unsigned char) * (nr_times * nr_subbands *
        nr_channels * nr_polarizations ), cudaMemcpyHostToDevice)) !=
        cudaSuccess){
    fprintf(stderr, "Error copying flags to device:\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
    exit(1);
  }



  result->flagged = flagged;
  result->initial_flagged = initial_flagged;

  return result;
}

Device_array_pointers* malloc_cuda_memory(Data_info* h_data_info, Device_data** d_data){
  cudaError_t cErr;

  Device_data* h_data = (Device_data*) malloc(sizeof(Device_data)); 

  h_data->nr_stations         = h_data_info->nr_stations;
  h_data->nr_times            = h_data_info->nr_times;
  h_data->nr_subbands         = h_data_info->nr_subbands;
  h_data->nr_subbands_in_file = h_data_info->nr_subbands_in_file;
  h_data->nr_channels         = h_data_info->nr_channels;
  h_data->integration_factor  = h_data_info->integration_factor;
  h_data->nr_polarizations    = h_data_info->nr_polarizations;
  h_data->station_block_size  = h_data_info->station_block_size;

  if((cErr = cudaMalloc(&(*d_data), sizeof(Device_data))) != cudaSuccess){
    fprintf(stderr, "Error allocating device memory for data_info struct");
    fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
    exit(1);
  }
  if((cErr = cudaMemcpy(*d_data, h_data, sizeof(Device_data),
          cudaMemcpyHostToDevice)) != cudaSuccess){
    fprintf(stderr, "Error copying data_info to device\n");
    fprintf(stderr, "%s\n", cudaGetErrorString(cErr));
    exit(1);
  }
  return allocate_cuda_memory(h_data_info);
}


