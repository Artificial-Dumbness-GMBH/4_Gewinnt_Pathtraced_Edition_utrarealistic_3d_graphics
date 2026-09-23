#include "dx12_renderer.h"
#include <jni.h>
static Backend& backend(jlong h) { if(!h) throw std::runtime_error("Closed DX12 backend");return *reinterpret_cast<Backend*>(h); }
static void report(JNIEnv* env,const std::exception& e) {
    if(!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());
}
#define JNI_METHOD(name) Java_de_viergewinnt_renderer_DirectX12Backend_00024Native_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(create)(JNIEnv* env,jclass,jboolean debug) {
    try { auto b=std::make_unique<Backend>();b->init(debug);return reinterpret_cast<jlong>(b.release()); }catch(const std::exception& e) { report(env,e);return 0; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(destroy)(JNIEnv*,jclass,jlong h) { delete reinterpret_cast<Backend*>(h); }
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(adapterName)(JNIEnv* env,jclass,jlong h) {
    try { DXGI_ADAPTER_DESC1 d{};checked(backend(h).adapter->GetDesc1(&d),"Adapter name");return env->NewString(reinterpret_cast<const jchar*>(d.Description),jsize(wcslen(d.Description))); }catch(const std::exception& e) { report(env,e);return nullptr; }
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(capabilities)(JNIEnv* env,jclass,jlong h) {
    try { auto& b=backend(h);return (b.dxr?1:0)|(b.vendor.fsrAvailable()?2:0)|(b.vendor.xessAvailable()?4:0)|(1<<8); }catch(const std::exception& e) { report(env,e);return 0; }
}
extern "C" JNIEXPORT jstring JNICALL JNI_METHOD(status)(JNIEnv* env,jclass,jlong h) {
    try { return env->NewStringUTF(backend(h).info.c_str()); }catch(const std::exception& e) { report(env,e);return nullptr; }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(attach)(JNIEnv* env,jclass,jlong h,jlong window,jint w,jint height) {
    try { if(!window||w<1||height<1) throw std::runtime_error("Invalid window");backend(h).attach(reinterpret_cast<HWND>(window),UINT(w),UINT(height)); }catch(const std::exception& e) { report(env,e); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(resize)(JNIEnv* env,jclass,jlong h,jint w,jint height) {
    try { if(w>0&&height>0) backend(h).resize(UINT(w),UINT(height)); }catch(const std::exception& e) { report(env,e); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(configure)(JNIEnv* env,jclass,jlong h,jintArray ints,jfloatArray floats) {
    try {
        if(!ints||!floats||env->GetArrayLength(ints)!=12||env->GetArrayLength(floats)!=3) throw std::runtime_error("Invalid settings ABI");
        int v[12];float f[3];env->GetIntArrayRegion(ints,0,12,v);env->GetFloatArrayRegion(floats,0,3,f);if(env->ExceptionCheck()) return;
        if(v[0]<0||v[0]>2||v[1]<0||v[1]>2||v[2]<0||v[2]>3||v[3]<0||v[3]>2||v[4]<1||v[4]>12||v[5]<1||v[5]>16
            ||v[6]<64||v[6]>3840||v[7]<64||v[7]>2160||v[8]<1||v[8]>5||v[9]<0||v[9]>1||v[10]<0||v[10]>1||v[11]<1||v[11]>4
            ||!std::isfinite(f[0])||f[0]<.25f||f[0]>3||!std::isfinite(f[1])||f[1]<.25f||f[1]>2||!std::isfinite(f[2])||f[2]<0||f[2]>1) throw std::runtime_error("Invalid settings");
        auto& b=backend(h);if(!b.width||!b.height) throw std::runtime_error("Attach window before configure");b.configure(v,f);
    }catch(const std::exception& e) { report(env,e); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(setScene)(JNIEnv* env,jclass,jlong h,jobjectArray buffers,jint moving) {
    try {
        if(!buffers||env->GetArrayLength(buffers)!=4) throw std::runtime_error("Invalid scene ABI");
        std::array<const void*,4> data{};std::array<size_t,4> sizes{};std::array<jobject,4> refs{};const int strides[]={16,16,48,48};
        for(int i=0;i<4;i++) {
            refs[i]=env->GetObjectArrayElement(buffers,i);if(!refs[i]) throw std::runtime_error("Null scene buffer");
            auto size=env->GetDirectBufferCapacity(refs[i]);data[i]=env->GetDirectBufferAddress(refs[i]);
            if(!data[i]||size<=0||size%strides[i]!=0||size>256*1024*1024) throw std::runtime_error("Invalid direct scene buffer");
            sizes[i]=size;
        }
        if(moving<-1||(moving>=0&&size_t(moving)>=sizes[0]/16)) throw std::runtime_error("Invalid moving vertex");
        const UINT* tri=static_cast<const UINT*>(data[1]);
        for(size_t i=0;i<sizes[1]/4;i+=4) if(tri[i]>=sizes[0]/16||tri[i+1]>=sizes[0]/16||tri[i+2]>=sizes[0]/16||tri[i+3]>=sizes[2]/48) throw std::runtime_error("Invalid triangle indices");
        backend(h).scene(data,sizes,moving);
        for(auto r:refs) env->DeleteLocalRef(r);
    }catch(const std::exception& e) { report(env,e); }
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(render)(JNIEnv* env,jclass,jlong h,jfloatArray camera,jfloat lift,jfloat ms,jobject ui,jint mode) {
    try {
        if(!camera||env->GetArrayLength(camera)!=16||!std::isfinite(lift)||!std::isfinite(ms)||mode<0||mode>2) throw std::runtime_error("Invalid frame ABI");
        float c[16];env->GetFloatArrayRegion(camera,0,16,c);if(env->ExceptionCheck()) return;
        for(float f:c) if(!std::isfinite(f)) throw std::runtime_error("Invalid camera");
        const void* bytes=ui?env->GetDirectBufferAddress(ui):nullptr;
        if(mode!=0&&(!bytes||env->GetDirectBufferCapacity(ui)!=960*660*4)) throw std::runtime_error("Invalid UI buffer");
        backend(h).render(c,lift,std::clamp(ms,.1f,1000.f),bytes,mode);
    }catch(const std::exception& e) { report(env,e); }
}
