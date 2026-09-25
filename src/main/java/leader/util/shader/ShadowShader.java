package leader.util.shader;

import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class ShadowShader extends Shader {

    private static final String FRAGMENT =
            "#version 120\n" +
            "\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 texelSize, direction;\n" +
            "uniform float radius;\n" +
            "uniform float weights[256];\n" +
            "\n" +
            "void main() {\n" +
            "    if (direction.y > 0 && texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;\n" +
            "    float blr = texture2D(inTexture, gl_TexCoord[0].st).a * weights[0];\n" +
            "    for (float f = 1.0; f <= radius; f++) {\n" +
            "        blr += texture2D(inTexture, gl_TexCoord[0].st + f * texelSize * direction).a * weights[int(abs(f))];\n" +
            "        blr += texture2D(inTexture, gl_TexCoord[0].st - f * texelSize * direction).a * weights[int(abs(f))];\n" +
            "    }\n" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, blr);\n" +
            "}\n";

    public ShadowShader() {
        super(FRAGMENT);
    }

    @Override
    public void onLink() {
    }

    @Override
    public void onUse() {
    }

    public void setInTexture(int tex) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "inTexture"), tex);
    }

    public void setTextureToCheck(int tex) {
        GL20.glUniform1i(GL20.glGetUniformLocation(programId, "textureToCheck"), tex);
    }

    public void setTexelSize(float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "texelSize"), x, y);
    }

    public void setDirection(float x, float y) {
        GL20.glUniform2f(GL20.glGetUniformLocation(programId, "direction"), x, y);
    }

    public void setRadius(float r) {
        GL20.glUniform1f(GL20.glGetUniformLocation(programId, "radius"), r);
    }

    public void setWeights(FloatBuffer buffer) {
        GL20.glUniform1(GL20.glGetUniformLocation(programId, "weights"), buffer);
    }
}
