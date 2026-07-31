package leader.client.module.values;

import java.util.List;

/**
 * Anything that owns a list of {@link Value}s (a Module, a Mode, a CompositeValue).
 * Values self-register into their parent holder on construction.
 */
public interface ValueHolder {
    List<Value<?>> getValues();
}
