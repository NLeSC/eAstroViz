#include <stdio.h>
#include <limits.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <arpa/inet.h>


#define MAX_SUBBANDS INT_MAX
#define MAX_SEQ_NR   INT_MAX
#define TRUE         1
#define FALSE        0


typedef struct {
  int nr_stations;
  int nr_times;
  int nr_subbands;
  int nr_subbands_in_file;
  int nr_channels;
  int nr_polarizations;
  int integration_factor;
  int station_block_size;
  float**** data;
  unsigned char*** flagged;
  unsigned char*** initial_flagged;
} Data_info;

/* Converts four big endian ordered bytes into a float */
float bytes2float(unsigned char *raw){
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



Data_info* read_file(char* file_name)
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

  printf("Parameters read! nr_stations: %d, nr_times: %d, nr_subbands: %d, nr_channels: %d, nr_polarizations: %d\n", nr_stations, nr_times, nr_subbands_in_file, nr_channels, nr_polarizations);

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
            sample = bytes2float(&byte_buffer[index * sizeof(float)]);;
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
  data_info = malloc(sizeof(Data_info));
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

  return data_info;
}

void allocate_cuda_memory(Data_info* d_data_info){
  int nr_times = d_data_info->nr_times;
  int nr_subbands = d_data_info->nr_subbands;
  int nr_channels = d_data_info->nr_channels;
  int nr_polarizations = d_data_info->nr_polarizations;
  float**** data = d_data_info->data;
  unsigned char*** flagged = d_data_info->flagged;
  unsigned char*** initial_flagged = d_data_info->flagged;
  int i,j,k;

  data = (float****) cudaMalloc(sizeof(float***) * nr_times);
  if(data == NULL){
    perror("Error allocating memory for data buffer:");
    exit(1);
  }
  flagged = (unsigned char***) cudaMalloc(sizeof(unsigned char **) * nr_times);
  if(flagged == NULL){

    perror("Error allocating memory for flag buffer:");
    exit(1);
  }
  initial_flagged = (unsigned char***) cudaMalloc(sizeof(unsigned char **) * nr_times);
  if(initial_flagged == NULL){
    perror("Error allocating memory for flag buffer:");
    exit(1);
  }

  for(i=0;i<nr_times;i++){
    data[i] = (float ***) cudaMalloc(sizeof(float**) * nr_subbands);
    if(data[i] == NULL){
      perror("Error allocating memory for data on time");
      exit(1);
    }
    flagged[i] = (unsigned char **) cudaMalloc(sizeof(unsigned char *) * nr_subbands);
    if(flagged[i] == NULL){
      perror("Error allocating memory for flags on time");
      exit(1);
    }
    initial_flagged[i] = (unsigned char **) cudaMalloc(sizeof(unsigned char *) 
    * nr_subbands);
    if(initial_flagged[i] == NULL){
      perror("Error allocating memory for initial_flags on time");
      exit(1);
    }

    for(j=0;j<nr_subbands;j++){
      data[i][j] = (float **) cudaMalloc(sizeof(float*) * nr_polarizations);
      if(data[i][j] == NULL){
        perror("Error allocating memory for data on subband at time");
        exit(1);
      }

      for(k=0;k<nr_polarizations;k++){
        data[i][j][k] = (float *) cudaMalloc(sizeof(float) * nr_channels);
        if(data[i][j][k] == NULL){
          perror("Error allocating memory for data on on polarization on subband at time ");
          exit(1);
        }
        cudaMemset(data[i][j][k], 0, sizeof(float) * nr_channels);
      }
      flagged[i][j] = (unsigned char*) cudaMalloc(sizeof(unsigned char) * nr_channels);
      if(flagged[i][j] == NULL){
        perror("Error allocating memory for flags on subband at time");
        exit(1);
      }
      initial_flagged[i][j] = (unsigned char*) cudaMalloc(sizeof(unsigned char) *
      nr_channels);
      if(initial_flagged[i][j] == NULL){
        perror("Error allocating memory for initial flags on subband at time");
        exit(1);
      }
      cudaMemset(flagged[i][j], 0, sizeof(unsigned char) * nr_channels);
      cudaMemset(initial_flagged[i][j], 0, sizeof(unsigned char) * nr_channels);
    }
  }
}

void malloc_cuda_memory(Data_info* h_data_info, Data_info* d_data_info){
  d_data_info = cudaMalloc(sizeof(Data_info));
  d_data_info->nr_stations         = h_data_info->nr_stations;
  d_data_info->nr_times            = h_data_info->nr_times;
  d_data_info->nr_subbands         = h_data_info->nr_subbands;
  d_data_info->nr_subbands_in_file = h_data_info->nr_subbands_in_file;
  d_data_info->nr_channels         = h_data_info->nr_channels;
  d_data_info->integration_factor  = h_data_info->integration_factor;
  d_data_info->nr_polarizations    = h_data_info->nr_polarizations;
  d_data_info->station_block_size  = h_data_info->station_block_size;
  allocate_cuda_memory(d_data_info);
}

int main(int argc, char** argv){
  float**** d_data = NULL;
  unsigned char*** d_flagged = NULL;
  unsigned char*** d_initial_flagged;
  Data_info* h_data_info = NULL;
  Data_info* d_data_info = NULL;
  int i;

  if(argc != 2){
    printf("Usage: flagger <filename>\n");
    exit(1);
  }

  h_data_info = read_file(argv[1]); 
  malloc_cuda_memory(&h_data_info, &d_data_info);
  if(h_data_info->nr_stations != d_data_info->nr_stations){
    fprintf(stderr, "Something went wrong allocating CUDA memory");
    exit(1);
  }

  return 0;


}

