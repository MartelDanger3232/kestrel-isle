#import "Common/ShaderLib/GLSLCompat.glsllib"
uniform vec4 m_Zenith;
uniform vec4 m_Horizon;
uniform vec3 m_SunDir;
uniform float m_SunVis;
uniform float m_Night;
uniform float m_Cover;
uniform float m_Drift;
uniform vec4 m_CloudCol;
uniform sampler2D m_Clouds;
varying vec3 dir;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

void main() {
    vec3 d = normalize(dir);
    vec3 c = mix(m_Horizon.rgb, m_Zenith.rgb, d.y <= 0.0 ? 0.0 : sqrt(d.y));
    float sd = dot(d, m_SunDir), sun = 0.0;
    if (sd > 0.0 && m_SunVis > 0.0) {
        sun = (sd > 0.9994 ? 1.0 : pow(sd, 32.0) * 0.45) * m_SunVis;
        c = mix(c, vec3(1.0, 0.957, 0.839), sun);
    } else if (sd < -0.9993 && m_Night > 0.0) {
        c = mix(c, vec3(0.91, 0.925, 0.98), m_Night);
    }
    // Stars: a few cells of a fine grid over the sky each hold a small soft point.
    if (m_Night > 0.0 && d.y > 0.0) {
        vec3 p = d * 420.0, cell = floor(p);
        if (hash(cell) < 0.0045) {
            float k = smoothstep(0.5, 0.05, length(fract(p) - 0.5));
            c = mix(c, vec3(1.0), m_Night * k * (0.45 + 0.55 * hash(cell + 7.0)));
        }
    }
    // Clouds sit on a flat layer overhead; the texture tiles and the wind slides it along.
    float a = 0.0;
    if (d.y > 0.02 && m_Cover > 0.0) {
        float k = 1.0 / d.y;
        vec2 uv = vec2(d.x * k * 90.0 + m_Drift, -d.z * k * 90.0 + m_Drift * 0.4) / 512.0;
        float v = texture2D(m_Clouds, uv).r * 2.0;
        a = smoothstep(1.0 - m_Cover, 1.3 - m_Cover, v) * min(1.0, (d.y - 0.02) * 7.0) * 0.92;
        c = mix(c, m_CloudCol.rgb, a);
    }
    #ifdef GLOW_PASS
        gl_FragColor = vec4(vec3(1.0, 0.92, 0.75) * sun * (1.0 - a), 1.0);
    #else
        gl_FragColor = vec4(c, 1.0);
    #endif
}
