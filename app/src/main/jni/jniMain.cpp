//
// Created by cheon on 18. 9. 26.
//

#include <com_example_cheon_androidcameraapi_CameraPreview.h>
//#include <string.h>

JNIEXPORT jstring JNICALL Java_com_example_cheon_androidcameraapi_CameraPreview_getJNIString(JNIEnv *env, jobject obj) {

    return env->NewStringUTF("Message from jniMain");
}