#ifndef DATA_INFO_INCLUDED
#define DATA_INFO_INCLUDED

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


#endif
