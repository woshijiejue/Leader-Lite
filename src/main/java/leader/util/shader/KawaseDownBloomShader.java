package leader.util.shader;

import org.lwjgl.opengl.GL20;

public class KawaseDownBloomShader extends Shader {

    private static final String FRAGMENT =
            "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, gl_TexCoord[0].st);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    sum *= 4.0;\n" +
            "    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    smp1.rgb *= smp1.a;\n" +
            "    sum += smp1;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 result = sum / 8.0;\n" +
            "    gl_FragColor = result.a > 0.0 ? vec4(result.rgb / result.a, result.a) : vec4(0.0);\n" +
            "}\n";

    public KawaseDownBloomShader() {
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

    public void setResolution(float w, float h) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "iResolution"), w, h);
    }
}
