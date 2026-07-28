#include <jni.h>

// Mock data updates
extern "C" JNIEXPORT void JNICALL
Java_com_hermes_obd2_qcm_nativeui_ObdViewModel_updateData(
    JNIEnv* env, 
    jclass clazz, 
    jint water, 
    jint oil, 
    jdouble afr, 
    jint boost);

// Example of a raw timestamp getter
extern "C" JNIEXPORT jlong JNICALL
Java_com_hermes_obd2_qcm_nativeui_ObdViewModel_getNativeTimestamp(
    JNIEnv* env, 
    jclass clazz);
