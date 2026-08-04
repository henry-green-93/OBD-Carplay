#ifndef NATIVES_H
#define NATIVES_H

#include <jni.h>

extern "C" {

// Mock data updates
JNIEXPORT void JNICALL
Java_com_henryg_obdcarplay_vm_ObdViewModel_updateData(
    JNIEnv* env,
    jclass clazz,
    jint water,
    jint oil,
    jdouble afr,
    jint boost);

// Example of a raw timestamp getter
JNIEXPORT jlong JNICALL
Java_com_henryg_obdcarplay_vm_ObdViewModel_getNativeTimestamp(
    JNIEnv* env,
    jclass clazz);

}

#endif // NATIVES_H
