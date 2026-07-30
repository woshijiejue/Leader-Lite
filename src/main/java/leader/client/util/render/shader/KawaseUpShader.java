package leader.client.util.render.shader;

import org.lwjgl.opengl.GL20;

public class KawaseUpShader extends Shader {

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
            "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;\n" +
            "    sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;\n" +
            "\n" +
            "    gl_FragColor = vec4(sum.rgb /12.0, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));\n" +
            "}\n";

    public KawaseUpShader() {
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
