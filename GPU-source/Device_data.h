#ifndef DEVICE_DATA_INCLUDED
#define DEVICE_DATA_INCLUDED

typedef struct {
  int nr_stations;
  int nr_times;
  int nr_subbands;
  int nr_subbands_in_file;
  int nr_channels;
  int nr_polarizations;
  int integration_factor;
  int station_block_size;
} Device_data;


#endif
