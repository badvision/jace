package jace.hardware;

import java.util.function.Supplier;

import jace.apple2e.VideoDHGR;
import jace.apple2e.VideoNTSC;
import jace.config.DeviceEnum;
import jace.core.Video;

public enum VideoImpls implements DeviceEnum<Video> {
    DHGR("Double-Hires graphics", VideoDHGR.class, VideoDHGR::new),
    NTSC("NTSC Emulation", VideoNTSC.class, VideoNTSC::new);

    private final String name;
    private final Class<? extends Video> clazz;
    private final Supplier<Video> factory;

    VideoImpls(String name, Class<? extends Video> clazz, Supplier<Video> factory) {
        this.name = name;
        this.clazz = clazz;
        this.factory = factory;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Video create() {
        return factory.get();
    }

    @Override
    public boolean isInstance(Video video) {
        return video != null && clazz.equals(video.getClass());
    }
}