#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/MaterialFog.glsllib"
uniform float m_Alpha;
uniform float m_GlowFrom;
uniform float m_GlowPower;
#ifdef HAS_COLORMAP
    uniform sampler2D m_ColorMap;
#endif
varying vec4 vColor;
varying vec2 vUv;

void main() {
    vec4 c = vColor;
    c.a *= m_Alpha;
    #ifdef HAS_COLORMAP
        c *= texture2D(m_ColorMap, vUv);
    #endif
    #ifdef GLOW_PASS
        float lum = dot(c.rgb, vec3(0.3, 0.59, 0.11));
        gl_FragColor = vec4(c.rgb * smoothstep(m_GlowFrom, m_GlowFrom + 0.25, lum) * m_GlowPower, c.a);
    #else
        #ifdef USE_FOG
            float a = c.a;
            c = MaterialFog_calculateFogColor(c);
            c.a = a;
        #endif
        gl_FragColor = c;
    #endif
}
