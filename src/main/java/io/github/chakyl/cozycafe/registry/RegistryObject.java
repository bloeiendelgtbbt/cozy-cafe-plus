package io.github.chakyl.cozycafe.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Minimal stand-in for Forge's RegistryObject: Fabric registers immediately, so this just holds the value.
 */
public final class RegistryObject<T> implements Supplier<T> {
    private final ResourceLocation id;
    private final T value;

    private RegistryObject(ResourceLocation id, T value) {
        this.id = id;
        this.value = value;
    }

    public static <R, T extends R> RegistryObject<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> supplier) {
        T value = Registry.register(registry, id, supplier.get());
        return new RegistryObject<>(id, value);
    }

    @Override
    public T get() {
        return this.value;
    }

    public ResourceLocation getId() {
        return this.id;
    }
}
