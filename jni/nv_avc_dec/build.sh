rm *.o libnv_avc_dec.so
gcc-4.9 -I ${JAVA_HOME}/include/ -I ${JAVA_HOME}/include/darwin -I ./inc -fPIC -L. -c *.c
gcc-4.9 -shared -Wl,-install_name,libnv_avc_dec.dylib -Wl,-undefined,dynamic_lookup -o libnv_avc_dec.dylib *.o -L. -lavcodec -lavfilter -lavformat -lavutil -lswresample -lswscale
rm *.o
