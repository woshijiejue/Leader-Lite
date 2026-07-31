package leader.client.util;

/**
 * Simple mutable single-value holder. Lives outside the {@code leader.mixin}
 * package because {@code FMLLoadingPlugin} registers every class under that
 * package as a mixin, and this is a plain helper (no {@code @Mixin}).
 */
public class Box<T> {
    public T value;

    public Box(T value) {
        this.value = value;
    }
}
