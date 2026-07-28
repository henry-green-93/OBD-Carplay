#include <jni.h>
#include <cmath>
#include <ctime>

// Global variables for mock behavior
float water_timer = 0.0f;
float oil_timer = 0.0f;

// We need to hold references to the ViewModel to update variables easily
jclass vm_class;
jmethodID update_data_id;
jfieldID water_temp_id;
jfieldID oil_temp_id;
jfieldID afr_boost_id;

extern "C" JNIEXPORT void JNICALL
Java_com_hermes_obd2_qcm_nativeui_ObdViewModel_updateData(
    JNIEnv* env, 
    jobject obj, 
    jint water, 
    jint oil, 
    jdouble afr, 
    jint boost) {
    
    // Get the class and IDs (Simplified for this scaffold)
    // In a full implementation, we'd do this once in an init function
    // to save CPU cycles every 16ms.
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hermes_obd2_qcm_nativeui_ObdViewModel_getNativeTimestamp(
    JNIEnv* env, 
    jclass clazz) {
    return (jlong)std::time(nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hermes_obd2_qcm_nativeui_ObdViewModel_fetchNextMockData(
    JNIEnv* env, 
    jobject obj) {
    
    // Get the ViewModel instance
    jclass clazz = env->GetObjectClass(obj);
    
    // For a truly perfect 60Hz loop, we'd pre-calculate these IDs in a 'init'
    // But for this scaffold, we'll just compute the values:
    water_timer += 0.1f;
    oil_timer += 0.15f;

    int water = (int)(90 + 10 * sin(water_timer));
    int oil = (int)(70 + 10 * sin(oil_timer));
    double afr = 1.4 + 0.2 * cos(water_timer);
    int boost = 10 + (int)(5 * sin(oil_timer));

    // Update the data via JNI
    // (We'll refine the ID lookups in the next iteration)
    env->CallVoidMethod(obj, update_data_id, (jint)water, (jint)oil, (jdouble)afr, (jint)boost);
}
