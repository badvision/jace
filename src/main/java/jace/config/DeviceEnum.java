package jace.config;

public interface DeviceEnum<T extends Reconfigurable> {
    public String getName();
    public T create();
    public boolean isInstance(T t);
}