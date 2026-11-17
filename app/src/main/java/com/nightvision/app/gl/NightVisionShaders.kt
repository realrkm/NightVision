package com.nightvision.app.gl

import android.opengl.GLES30
import android.util.Log

/**
 * GLSL ES 3.00 shaders and compilation helpers for NightVisionRenderer.
 */
internal object NightVisionShaders {

    private const val TAG = "NightVisionShaders"

    const val OES_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uVertexMatrix;
uniform mat4 uTexMatrix;

out vec2 vTexCoord;
out vec2 vScreenUv;

void main() {
    gl_Position = uVertexMatrix * aPosition;
    vec4 transformedUv = uTexMatrix * vec4(aTexCoord, 0.0, 1.0);
    vTexCoord = transformedUv.xy;
    vScreenUv = aPosition.xy * 0.5 + 0.5;
}
"""

    const val OES_FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

in vec2 vTexCoord;
in vec2 vScreenUv;

uniform samplerExternalOES uCameraTex;
uniform int uMode;          // 0 = AI fallback, 1 = NVG Green, 2 = Mono Boost
uniform float uGain;
uniform float uContrast;
uniform int uScanlines;
uniform vec2 uResolution;

out vec4 fragColor;

float luminance(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 applyContrast(vec3 c, float contrast) {
    return clamp((c - 0.5) * contrast + 0.5, 0.0, 1.0);
}

float applyContrastScalar(float v, float contrast) {
    return clamp((v - 0.5) * contrast + 0.5, 0.0, 1.0);
}

void main() {
    vec3 src = texture(uCameraTex, vTexCoord).rgb;

    if (uMode == 1) {
        // GREEN_NIGHT_VISION: phosphor green channel mapping + shadow lift + scanlines + scope vignette
        float luma = luminance(src);
        float lifted = pow(clamp(luma * uGain, 0.0, 1.0), 0.78);
        float boosted = applyContrastScalar(lifted, uContrast);

        vec3 shadow = vec3(0.0, 0.04, 0.02);
        vec3 mid = vec3(0.04, 0.88, 0.36);
        vec3 highlight = vec3(0.68, 1.0, 0.80);

        vec3 nvg = boosted < 0.65
            ? mix(shadow, mid, boosted / 0.65)
            : mix(mid, highlight, (boosted - 0.65) / 0.35);

        if (uScanlines == 1) {
            float scan = 0.94 + 0.06 * sin(gl_FragCoord.y * 1.5);
            nvg *= scan;
        }

        // Subtle circular tactical scope vignette
        float aspect = uResolution.x / max(uResolution.y, 1.0);
        vec2 centered = vScreenUv - 0.5;
        centered.x *= aspect;
        float dist = length(centered);
        float vignette = 1.0 - smoothstep(0.45, 0.88, dist);
        nvg *= mix(0.35, 1.0, vignette);

        fragColor = vec4(nvg, 1.0);
    } else if (uMode == 2) {
        // HIGH_CONTRAST_BW: crisp monochrome low-light boost with shadow lift
        float luma = luminance(src);
        float lifted = pow(clamp(luma * uGain, 0.0, 1.0), 0.68);
        float mono = applyContrastScalar(lifted, uContrast * 1.15);
        vec3 tinted = vec3(mono * 0.96, mono * 0.98, mono);
        fragColor = vec4(tinted, 1.0);
    } else {
        // AI_LOW_LIGHT fallback before first AI bitmap arrives: clean shadow-lifted color
        vec3 lifted = pow(clamp(src * uGain, 0.0, 1.0), vec3(0.75));
        vec3 finalRgb = applyContrast(lifted, uContrast);
        fragColor = vec4(finalRgb, 1.0);
    }
}
"""

    const val AI_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uVertexMatrix;

out vec2 vTexCoord;

void main() {
    gl_Position = uVertexMatrix * aPosition;
    // Bitmap uploaded via GLUtils.texImage2D has row 0 at v=0, so flip V for upright display.
    vTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y);
}
"""

    const val AI_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;

uniform sampler2D uAiTex;
uniform float uGain;
uniform float uContrast;

out vec4 fragColor;

void main() {
    vec3 color = texture(uAiTex, vTexCoord).rgb;
    float gentleGain = mix(1.0, uGain, 0.30);
    float gentleContrast = mix(1.0, uContrast, 0.45);
    color = clamp(color * gentleGain, 0.0, 1.0);
    color = clamp((color - 0.5) * gentleContrast + 0.5, 0.0, 1.0);
    fragColor = vec4(color, 1.0);
}
"""

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        if (program == 0) {
            throw RuntimeException("Failed to create OpenGL ES program")
        }
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val info = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $info")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Failed to create shader of type $type")
        }
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compile error: $info")
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $info")
        }
        return shader
    }
}
