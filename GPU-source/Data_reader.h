#ifndef DATA_READER_INCLUDED
#define DATA_READER_INCLUDED

#include "Data_info.h"
#include "Device_array_pointers.h"
#include "Device_data.h"

float bytes2floatLE(unsigned char *raw);
Data_info* read_file(char* filename, int* meta_data, int little_endian);
Data_info* fake_data(int nr_times, int nr_subbands, int nr_channels, int nr_polarizations);
Device_array_pointers* malloc_cuda_memory(Data_info* h_data_info, Device_data** d_data);

#endif
