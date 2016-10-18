#ifndef DEVICE_ARRAY_POINTERS_INCLUDED
#define DEVICE_ARRAY_POINTERS_INCLUDED

typedef struct {
  float* data;
  unsigned char* flagged;
  unsigned char* initial_flagged;
} Device_array_pointers;

#endif
