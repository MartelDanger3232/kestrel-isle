#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;
uniform vec3 g_CameraPosition;
attribute vec3 inPosition;
attribute vec4 inColor;
attribute vec2 inTexCoord;
varying vec4 vColor;
varying vec2 vUv;
#ifdef USE_FOG
    varying float fogDistance;
#endif

void main() {
    vColor = inColor;
    vUv = inTexCoord;
    vec4 pos = vec4(inPosition, 1.0);
    gl_Position = g_WorldViewProjectionMatrix * pos;
    #ifdef USE_FOG
        fogDistance = distance(g_CameraPosition, (g_WorldMatrix * pos).xyz);
    #endif
}
