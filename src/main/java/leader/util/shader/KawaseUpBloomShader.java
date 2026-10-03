package leader.util.shader;

import org.lwjgl.opengl.GL20;

public class KawaseUpBloomShader extends Shader {

    private static final String FRAGMENT =
            "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform int check;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    vec4 smpl1 = texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);\n" +
            "    smpl1.rgb *= smpl1.a;\n" +
            "    sum += smpl1 * 2.0;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3 * 2.0;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp5.rgb *= smp5.a;\n" +
            "    sum += smp5 * 2.0;\n" +
            "    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    smp6.rgb *= smp6.a;\n" +
            "    sum += smp6;\n" +
            "    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp7.rgb *= smp7.a;\n" +
            "    sum += smp7 * 2.0;\n" +
            "    vec4 result = sum / 12.0;\n" +
            "    float na = result.a > 0.0 ? result.a : 0.0;\n" +
            "    gl_FragColor = vec4(na > 0.0 ? result.rgb / na : vec3(0.0), mix(na, na * (1.0 - texture2D(textureToCheck, gl_TexCoord[0].st).a), float(check)));\n" +
            "}\n";

    public KawaseUpBloomShader() {
        super(FRAGMENT);
    }

    @Override
    public void onLink() {
    }

    @Override
    public void onUse() {
    }

    public void setOffset(float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "offset"), x, y);
    }

    public void setHalfPixel(float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "halfpixel"), x, y);
    }

    public void setInTexture(int tex) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "inTexture"), tex);
    }

    public void setCheck(int val) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "check"), val);
    }

    public void setTextureToCheck(int tex) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "textureToCheck"), tex);
    }

    public void setResolution(float w, float h) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "iResolution"), w, h);
    }
}
