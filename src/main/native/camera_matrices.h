#pragma once
#include <cstring>
// Row-major matrices multiplying row vectors, matching the SDK conventions.
inline void cameraMatrices(const float* c,float aspect,float* view,float* projection) {
    std::memset(view,0,16*sizeof(float));std::memset(projection,0,16*sizeof(float));
    for(int i=0;i<3;i++) { view[i*4]=c[8+i];view[i*4+1]=c[12+i];view[i*4+2]=c[4+i]; }
    for(int axis=0;axis<3;axis++) for(int i=0;i<3;i++) view[12+axis]-=c[i]*view[i*4+axis];
    view[15]=1;projection[0]=1.73205080757f/aspect;projection[5]=1.73205080757f;
    projection[10]=1000.f/999.9f;projection[11]=1;projection[14]=-100.f/999.9f;
}
