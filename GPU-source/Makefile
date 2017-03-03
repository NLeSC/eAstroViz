CC=nvcc
CFLAGS=-Xcompiler -rdynamic -lineinfo -G -g -gencode arch=compute_35,code=sm_35 -gencode arch=compute_52,code=sm_52 --use_fast_math --prec-div=true -O3
CUDA_LDFLAGS = -lrt -lcudart
CUDA_LIBS = -L"$(CUDA_INSTALL_PATH)/lib64/"

all: flagger

flagger: flagger.o Data_reader.o timer.o
	$(CC) $(CFLAGS) flagger.o Data_reader.o timer.o -o flagger $(CUDA_LDFLAGS) $(CUDA_LIBS)

flagger.o: flagger.cu 
	$(CC) $(CFLAGS) -c flagger.cu $(CUDA_LDFLAGS) $(CUDA_LIBS)

timer.o: timer.cc
	g++ -c timer.cc -o timer.o

Data_reader.o: Data_reader.cu 
	$(CC) $(CFLAGS) -c Data_reader.cu $(CUDA_LDFLAGS) $(CUDA_LIBS)

clean: 
	rm -rf *.o flagger *~

