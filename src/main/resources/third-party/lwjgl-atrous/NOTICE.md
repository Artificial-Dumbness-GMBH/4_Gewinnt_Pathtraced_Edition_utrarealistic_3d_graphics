# LWJGL À-Trous denoiser

Upstream: https://github.com/LWJGL/lwjgl3-demos
Revision: 0846b5d965e3015c556ac26b802b63f8ea8aa129
Source: res/org/lwjgl/demo/opengl/raytracing/tutorial5/atrous.fs.glsl
License: BSD 3-Clause (LICENSE.txt). Original shader preserved alongside this notice.

The runnable adaptation is /shaders/atrous.comp. It retains the upstream
25-tap edge-avoiding wavelet weighting and runs four ping-pong passes with
step widths 1, 2, 4, 8. Adaptations: compute dispatch, bounds checks, depth-based
world-position reconstruction, plane-distance weights, material rejection,
albedo edge weights without remodulating antialiased radiance, configurable strength and finite denominators.
This is a spatial filter, not a neural denoiser or temporal reprojection.

Algorithm: Dammertz et al., Edge-Avoiding À-Trous Wavelet Transform for fast
Global Illumination Filtering (2010), https://jo.dreggn.org/home/2010_atrous.pdf
