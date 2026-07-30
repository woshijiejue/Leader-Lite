package leader.client.util.render.shader;

import org.lwjgl.opengl.GL20;

public class KawaseDownShader extends Shader {

    private static final String FRAGMENT =
            "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, gl_TexCoord[0].st) * 4.0;\n" +
            "    sum += texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    sum += texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    gl_FragColor = vec4(sum.rgb * .125, 1.0);\n" +
            "}\n";

    public KawaseDownShader() {
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
