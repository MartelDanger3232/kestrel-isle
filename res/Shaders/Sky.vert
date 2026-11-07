#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform mat4 g_ViewMatrix;
uniform mat4 g_ProjectionMatrix;
attribute vec3 inPosition;
varying vec3 dir;

void main() {
    // A dome that turns with the camera but never moves: only the view's rotation applies.
    dir = inPosition;
    vec4 pos = g_ViewMatrix * vec4(inPosition, 0.0);
    pos.w = 1.0;
    gl_Position = g_ProjectionMatrix * pos;
}
