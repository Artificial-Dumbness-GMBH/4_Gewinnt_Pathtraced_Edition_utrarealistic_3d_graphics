#include "camera_matrices.h"
#include <cmath>
#include <stdexcept>
static void check(bool v) { if(!v) throw std::runtime_error("Camera matrix convention mismatch"); }
int main() {
    // Camera at (3,2,-5), forward +Z, right +X, up +Y.
    float c[16]={3,2,-5,0,0,0,1,0,1,0,0,0,0,1,0,0},v[16],p[16];
    cameraMatrices(c,16.f/9,v,p);
    for(int axis=0;axis<3;axis++) {
        float cameraInView=v[12+axis];for(int i=0;i<3;i++) cameraInView+=c[i]*v[i*4+axis];
        check(std::abs(cameraInView)<1e-6f);
    }
    float nearDepth=(.1f*p[10]+p[14])/.1f,farDepth=(1000*p[10]+p[14])/1000;
    check(std::abs(nearDepth)<1e-5f&&std::abs(farDepth-1)<1e-5f);
    check(std::abs(p[5]/p[0]-16.f/9)<1e-5f&&v[15]==1&&p[11]==1);
    // A 90-degree turn preserves positive view depth and translates the origin.
    c[4]=1;c[6]=0;c[8]=0;c[10]=-1;cameraMatrices(c,1,v,p);
    check(std::abs(v[2]-1)<1e-6f&&std::abs(v[14]+3)<1e-6f);
}
