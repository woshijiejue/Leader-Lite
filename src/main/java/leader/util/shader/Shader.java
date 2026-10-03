package leader.util.shader;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public abstract class Shader {
    private static final String vertex = "#version 120\n" +
            "void main(void) {\n" +
            "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";
    private final Map<String, Integer> uniformLocations;
    protected int programId;

    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        int compile = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        return compile == 0 ? -1 : shader;
    }

    private void createProgram(String fragment) {
        int vertexShader = this.compileShader(vertex, GL20.GL_VERTEX_SHADER);
        int fragmentShader = this.compileShader(fragment, GL20.GL_FRAGMENT_SHADER);
        if (vertexShader == -1 || fragmentShader == -1) {
            if (vertexShader != -1) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != -1) {
                GL20.glDeleteShader(fragmentShader);
            }
            this.programId = 0;
            return;
        }

        this.programId = GL20.glCreateProgram();
        GL20.glAttachShader(this.programId, vertexShader);
        GL20.glAttachShader(this.programId, fragmentShader);
        GL20.glLinkProgram(this.programId);
        if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == 0) {
            GL20.glDeleteProgram(this.programId);
            this.programId = 0;
        } else {
            this.onLink();
        }
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    public Shader(String string) {
        this.uniformLocations = new HashMap<>();
        this.createProgram(string);
    }

    public int getUniformLocationCached(String name) {
        Integer location = this.uniformLocations.get(name);
        return location == null ? -1 : location;
    }

    public boolean isUsable() {
        return this.programId != 0 && GL20.glIsProgram(this.programId)
                && GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) != 0;
    }

    public void setUniform(String name) {
        this.uniformLocations.put(name, GL20.glGetUniformLocation(this.programId, name));
    }

    public abstract void onLink();

    public abstract void onUse();

    public void use() {
        if (isUsable()) {
            onUse();
        }
    }

    public void stop() {
        GL20.glUseProgram(0);
    }
}
